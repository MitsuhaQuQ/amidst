package amidst.mojangapi.minecraftinterface.modern;

import java.io.PrintStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import amidst.documentation.ThreadSafe;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.minecraftinterface.MinecraftInterface;
import amidst.mojangapi.minecraftinterface.MinecraftInterfaceCreationException;
import amidst.mojangapi.minecraftinterface.MinecraftInterfaceException;
import amidst.mojangapi.minecraftinterface.RecognisedVersion;
import amidst.mojangapi.minecraftinterface.UnsupportedDimensionException;
import amidst.mojangapi.minecraftinterface.modern.Mc.McException;
import amidst.mojangapi.minecraftinterface.modern.WorldgenApi.NoiseBiomes;
import amidst.mojangapi.minecraftinterface.modern.WorldgenApi.Resolver;
import amidst.mojangapi.minecraftinterface.modern.WorldgenApi.Stem;
import amidst.fragment.layer.LayerIds;
import amidst.mojangapi.world.Dimension;
import amidst.mojangapi.world.WorldOptions;
import amidst.mojangapi.world.WorldType;
import amidst.mojangapi.world.biome.BiomeList;
import amidst.mojangapi.world.coordinates.CoordinatesInWorld;
import amidst.mojangapi.world.icon.producer.MultiProducer;
import amidst.mojangapi.world.icon.type.DefaultWorldIconTypes;
import amidst.mojangapi.world.versionfeatures.FeatureKey;
import amidst.mojangapi.world.versionfeatures.VersionFeatures;
import amidst.util.ArrayCache;

/**
 * Talks to Minecraft 1.18 and later by the names of its classes. Those are
 * readable since 26.1; for the obfuscated versions before that, Amidst
 * loads a copy of the jar with the names of Mojang's obfuscation map.
 *
 * Since 1.18, Minecraft places biomes with a multi-noise biome source and
 * structures with the structure sets of its built-in data pack. Instead of
 * reimplementing them, this interface loads the data pack the way a server
 * does and asks Minecraft's own classes for biomes, the world spawn and the
 * places of structures.
 */
@ThreadSafe
public class ModernMinecraftInterface implements MinecraftInterface {
	private static final Set<Dimension> SUPPORTED_DIMENSIONS = Collections.unmodifiableSet(EnumSet.of(Dimension.OVERWORLD, Dimension.NETHER));
	/**
	 * Biomes are sampled at the top of the world. Up there, the climate picks
	 * the same biome as at the surface, but never one of the cave biomes.
	 */
	private static final int OVERWORLD_BIOME_Y = 316;
	private static final int NETHER_BIOME_Y = 64;
	private static final int QUART_SHIFT = 2;
	private static final int UNKNOWN_BIOME_ID = 127;

	public static ModernMinecraftInterface create(ClassLoader classLoader, RecognisedVersion recognisedVersion)
			throws MinecraftInterfaceCreationException {
		try {
			ModernMinecraftInterface result = new ModernMinecraftInterface(new Mc(classLoader), recognisedVersion);
			AmidstLogger.info("Loaded the worldgen data of Minecraft {}", recognisedVersion.getName());
			return result;
		} catch (McException | LinkageError e) {
			throw new MinecraftInterfaceCreationException("Unable to load the world generation of this Minecraft version", e);
		}
	}

	private final Mc mc;
	private final RecognisedVersion recognisedVersion;
	private final WorldgenApi api;
	private final Object resourceManager;
	private final Object registryAccess;
	private final Map<String, Integer> biomeIds;
	private final BiomeList biomeList;
	private final Map<Object, Integer> biomeIdCache = new ConcurrentHashMap<>();
	private final ArrayCache<int[]> dataArray = ArrayCache.makeIntArrayCache(256);
	private Object templateManager;
	private World world;

	private ModernMinecraftInterface(Mc mc, RecognisedVersion recognisedVersion) {
		this.mc = mc;
		this.recognisedVersion = recognisedVersion;
		bootstrap();
		this.api = WorldgenApi.create(mc);
		this.resourceManager = api.createResourceManager();
		this.registryAccess = api.loadRegistryAccess(resourceManager);
		this.biomeIds = ModernBiomes.assignIds(api.biomeKeys(registryAccess));
		this.biomeList = ModernBiomes.createBiomeList(biomeIds);
	}

	/**
	 * Minecraft replaces System.out and System.err while bootstrapping, so
	 * they are put back afterwards.
	 */
	private void bootstrap() {
		PrintStream out = System.out;
		PrintStream err = System.err;
		try {
			mc.callStatic("net.minecraft.SharedConstants", "tryDetectVersion");
			mc.callStatic("net.minecraft.server.Bootstrap", "bootStrap");
		} finally {
			System.setOut(out);
			System.setErr(err);
		}
	}

	private int biomeIdOf(Object biome) {
		Integer result = biomeIdCache.get(biome);
		if (result == null) {
			result = biomeIds.getOrDefault(api.biomeKey(registryAccess, biome), UNKNOWN_BIOME_ID);
			biomeIdCache.put(biome, result);
		}
		return result;
	}

	/**
	 * Structure templates are only needed for the start pieces of jigsaw
	 * structures like villages, so the template manager is made on first use.
	 */
	private synchronized Object getTemplateManager() {
		if (templateManager == null) {
			templateManager = api.createTemplateManager(resourceManager);
		}
		return templateManager;
	}

	private synchronized World worldFor(WorldOptions worldOptions) throws MinecraftInterfaceException {
		long seed = worldOptions.getWorldSeed().getLong();
		WorldType worldType = worldOptions.getWorldType();
		if (world == null || world.seed != seed || world.worldType != worldType) {
			try {
				world = new World(seed, worldType);
			} catch (McException e) {
				throw new MinecraftInterfaceException("unable to create the world", e);
			}
		}
		return world;
	}

	@Override
	public MinecraftInterface.WorldAccessor createWorldAccessor(WorldOptions worldOptions) throws MinecraftInterfaceException {
		World world = worldFor(worldOptions);
		try {
			return new WorldAccessor(world);
		} catch (McException e) {
			throw new MinecraftInterfaceException("unable to create the world", e);
		}
	}

	@Override
	public RecognisedVersion getRecognisedVersion() {
		return recognisedVersion;
	}

	@Override
	public void overrideFeatures(VersionFeatures.Builder features, WorldOptions worldOptions)
			throws MinecraftInterfaceException {
		World world = worldFor(worldOptions);
		ModernDimension overworld = world.overworld;
		ModernDimension nether = world.nether;
		features.withOverride(FeatureKey.BIOME_LIST, biomeList);
		features.withOverride(FeatureKey.WORLD_SPAWN_ORACLE, () -> {
			int[] chunk = overworld.getOriginChunk();
			return CoordinatesInWorld.from(chunk[0] << 4, chunk[1] << 4);
		});
		features.withOverride(FeatureKey.STRONGHOLD_PRODUCER, new ModernStructures.StrongholdProducer(overworld));
		features.withOverride(FeatureKey.VILLAGE_ICON_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.VILLAGE));
		features.withOverride(FeatureKey.PILLAGER_OUTPOST_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.PILLAGER_OUTPOST));
		features.withOverride(FeatureKey.DESERT_TEMPLE_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.DESERT));
		features.withOverride(FeatureKey.IGLOO_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.IGLOO));
		features.withOverride(FeatureKey.JUNGLE_TEMPLE_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.JUNGLE));
		features.withOverride(FeatureKey.WITCH_HUT_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.WITCH));
		features.withOverride(FeatureKey.MINESHAFT_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.MINESHAFT));
		features.withOverride(FeatureKey.OCEAN_MONUMENT_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.OCEAN_MONUMENT));
		features.withOverride(FeatureKey.WOODLAND_MANSION_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.WOODLAND_MANSION));
		features.withOverride(FeatureKey.OCEAN_RUINS_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.OCEAN_RUINS));
		features.withOverride(FeatureKey.SHIPWRECK_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.SHIPWRECK));
		features.withOverride(FeatureKey.BURIED_TREASURE_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.BURIED_TREASURE));
		features.withOverride(FeatureKey.ANCIENT_CITY_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.ANCIENT_CITY));
		features.withOverride(FeatureKey.TRIAL_CHAMBERS_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.TRIAL_CHAMBERS));
		features.withOverride(FeatureKey.TRAIL_RUINS_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.TRAIL_RUINS));
		features.withOverride(FeatureKey.ABANDONED_CAMP_PRODUCER, overworldStructures(overworld, DefaultWorldIconTypes.ABANDONED_CAMP));
		// The ruined portals of both dimensions share a layer
		features.withOverride(FeatureKey.RUINED_PORTAL_PRODUCER, new MultiProducer<>(
				overworldStructures(overworld, DefaultWorldIconTypes.RUINED_PORTAL),
				new ModernStructures.Producer(nether, Dimension.NETHER, DefaultWorldIconTypes.RUINED_PORTAL)));
		features.withOverride(FeatureKey.NETHER_FORTRESS_PRODUCER,
				new ModernStructures.Producer(nether, Dimension.NETHER, DefaultWorldIconTypes.NETHER_FORTRESS, DefaultWorldIconTypes.NETHER_FOSSIL));
		features.withOverride(FeatureKey.BASTION_REMNANT_PRODUCER,
				new ModernStructures.Producer(nether, Dimension.NETHER, DefaultWorldIconTypes.BASTION_REMNANT));
		features.withOverride(FeatureKey.LAYER_FILTER, layerId -> hasStructuresOf(layerId, overworld, nether));
	}

	/**
	 * Whether the loaded version places the structures of a layer, so the
	 * layers of structures added after 1.18 are only offered where they exist.
	 */
	private static boolean hasStructuresOf(int layerId, ModernDimension overworld, ModernDimension nether) {
		switch (layerId) {
		case LayerIds.RUINED_PORTALS:
			return overworld.hasStructures(DefaultWorldIconTypes.RUINED_PORTAL) || nether.hasStructures(DefaultWorldIconTypes.RUINED_PORTAL);
		case LayerIds.ANCIENT_CITIES:
			return overworld.hasStructures(DefaultWorldIconTypes.ANCIENT_CITY);
		case LayerIds.TRIAL_CHAMBERS:
			return overworld.hasStructures(DefaultWorldIconTypes.TRIAL_CHAMBERS);
		case LayerIds.TRAIL_RUINS:
			return overworld.hasStructures(DefaultWorldIconTypes.TRAIL_RUINS);
		case LayerIds.ABANDONED_CAMPS:
			return overworld.hasStructures(DefaultWorldIconTypes.ABANDONED_CAMP);
		case LayerIds.BASTION_REMNANTS:
			return nether.hasStructures(DefaultWorldIconTypes.BASTION_REMNANT);
		default:
			return true;
		}
	}

	private static ModernStructures.Producer overworldStructures(ModernDimension overworld, DefaultWorldIconTypes... types) {
		return new ModernStructures.Producer(overworld, Dimension.OVERWORLD, types);
	}

	/**
	 * The dimensions of a world, as Minecraft creates them for a new world
	 * of the world type.
	 */
	private class World {
		private final long seed;
		private final WorldType worldType;
		private final ModernDimension overworld;
		private final ModernDimension nether;
		private final long biomeZoomSeed;

		private World(long seed, WorldType worldType) {
			this.seed = seed;
			this.worldType = worldType;
			Map<String, Stem> stems = api.createStems(registryAccess, worldType, seed);
			this.overworld = createDimension(stems, WorldgenApi.OVERWORLD);
			this.nether = createDimension(stems, WorldgenApi.NETHER);
			this.biomeZoomSeed = (Long) mc.callStatic(WorldgenApi.BIOME_MANAGER, "obfuscateSeed", seed);
		}

		private ModernDimension createDimension(Map<String, Stem> stems, String name) {
			Stem stem = stems.get(name);
			if (stem == null) {
				throw new McException("the world has no dimension " + name);
			}
			return new ModernDimension(mc, api, registryAccess, stem, seed, ModernMinecraftInterface.this::getTemplateManager);
		}
	}

	private class WorldAccessor implements MinecraftInterface.WorldAccessor {
		private final NoiseBiomes overworldQuarter;
		private final NoiseBiomes overworldBlocks;
		private final NoiseBiomes netherQuarter;
		private final NoiseBiomes netherBlocks;

		private WorldAccessor(World world) {
			Resolver overworld = world.overworld.createQuarterBiomes();
			Resolver nether = world.nether.createQuarterBiomes();
			this.overworldQuarter = overworld.biomes;
			this.overworldBlocks = world.overworld.createBlockBiomes(overworld, world.biomeZoomSeed);
			this.netherQuarter = nether.biomes;
			this.netherBlocks = world.nether.createBlockBiomes(nether, world.biomeZoomSeed);
		}

		@Override
		public <T> T getBiomeData(
				Dimension dimension,
				int x, int y, int width, int height,
				boolean useQuarterResolution, Function<int[], T> biomeDataMapper)
				throws MinecraftInterfaceException {
			NoiseBiomes biomes;
			int biomeY;
			switch (dimension) {
			case OVERWORLD:
				biomes = useQuarterResolution ? overworldQuarter : overworldBlocks;
				biomeY = OVERWORLD_BIOME_Y;
				break;
			case NETHER:
				biomes = useQuarterResolution ? netherQuarter : netherBlocks;
				biomeY = NETHER_BIOME_Y;
				break;
			default:
				throw new UnsupportedDimensionException(dimension);
			}
			int sampleY = useQuarterResolution ? biomeY >> QUART_SHIFT : biomeY;

			return dataArray.withArrayFaillible(width * height, data -> {
				try {
					for (int j = 0; j < height; j++) {
						for (int i = 0; i < width; i++) {
							Object biome = biomes.get(x + i, sampleY, y + j);
							data[i + j * width] = biomeIdOf(Objects.requireNonNull(biome));
						}
					}
				} catch (Throwable e) {
					throw new MinecraftInterfaceException("unable to get biome data", e);
				}
				return biomeDataMapper.apply(data);
			});
		}

		@Override
		public Set<Dimension> supportedDimensions() {
			return SUPPORTED_DIMENSIONS;
		}
	}
}
