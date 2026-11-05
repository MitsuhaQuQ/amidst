package amidst.mojangapi.minecraftinterface.modern;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import amidst.documentation.ThreadSafe;
import amidst.mojangapi.minecraftinterface.modern.Mc.McException;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureEntry;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureSet;
import amidst.mojangapi.world.WorldType;

/**
 * The world generation of Minecraft as Amidst uses it, behind the changes
 * Minecraft made to it since 1.18. Registries, dimensions, biomes and
 * structures are asked for in the same way for every version, and the
 * subclasses follow the renames and the moved responsibilities of the
 * versions they cover.
 */
@ThreadSafe
abstract class WorldgenApi {
	static final String REGISTRY = "net.minecraft.core.Registry";
	static final String REGISTRIES = "net.minecraft.core.registries.Registries";
	static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
	static final String HOLDER = "net.minecraft.core.Holder";
	static final String BLOCK_POS = "net.minecraft.core.BlockPos";
	static final String CHUNK_POS = "net.minecraft.world.level.ChunkPos";
	static final String LEVEL_STEM = "net.minecraft.world.level.dimension.LevelStem";
	static final String DIMENSION_TYPE = "net.minecraft.world.level.dimension.DimensionType";
	static final String BIOME_SOURCE = "net.minecraft.world.level.biome.BiomeSource";
	static final String BIOME_MANAGER = "net.minecraft.world.level.biome.BiomeManager";
	static final String NOISE_BIOME_SOURCE = "net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource";
	static final String CLIMATE_SAMPLER = "net.minecraft.world.level.biome.Climate$Sampler";
	static final String WORLD_GEN_SETTINGS = "net.minecraft.world.level.levelgen.WorldGenSettings";
	static final String NOISE_GENERATOR_SETTINGS = "net.minecraft.world.level.levelgen.NoiseGeneratorSettings";
	static final String FLAT_LEVEL_SOURCE = "net.minecraft.world.level.levelgen.FlatLevelSource";
	static final String FLAT_SETTINGS = "net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings";
	static final String SERVER_PACKS_SOURCE = "net.minecraft.server.packs.repository.ServerPacksSource";
	static final String VANILLA_PACK_RESOURCES = "net.minecraft.server.packs.VanillaPackResources";
	static final String PACK_TYPE = "net.minecraft.server.packs.PackType";
	static final String MULTI_PACK_RESOURCE_MANAGER = "net.minecraft.server.packs.resources.MultiPackResourceManager";
	static final String SIMPLE_RESOURCE_MANAGER = "net.minecraft.server.packs.resources.SimpleReloadableResourceManager";
	static final String STRUCTURE_TEMPLATE_MANAGER = "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager";
	static final String STRUCTURE_MANAGER = "net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager";
	static final String LEVEL_STORAGE_SOURCE = "net.minecraft.world.level.storage.LevelStorageSource";
	static final String DATA_FIXERS = "net.minecraft.util.datafix.DataFixers";

	static final String OVERWORLD = "OVERWORLD";
	static final String NETHER = "NETHER";

	/**
	 * The adapter for the Minecraft behind the given loader. Since 1.18.2,
	 * structures are placed by structure sets; 1.18 and 1.18.1 still placed
	 * each kind of structure by its own settings.
	 */
	static WorldgenApi create(Mc mc) {
		if (mc.hasType(StructureSetWorldgen.STRUCTURE_SET)) {
			return new StructureSetWorldgen(mc);
		}
		return new StructureFeatureWorldgen(mc);
	}

	/**
	 * The generator of a dimension with the height range of the dimension.
	 */
	static final class Stem {
		final Object generator;
		final int minY;
		final int height;

		Stem(Object generator, int minY, int height) {
			this.generator = generator;
			this.minY = minY;
			this.height = height;
		}
	}

	/**
	 * Biomes at the resolution of the multi-noise biome source, or at the
	 * resolution of blocks after the zoom of the biome manager.
	 */
	@FunctionalInterface
	interface NoiseBiomes {
		Object get(int x, int y, int z) throws Throwable;
	}

	/**
	 * The biomes at quarter resolution, with the object of Minecraft that
	 * produces them, if Minecraft has one that the biome manager can use.
	 */
	static final class Resolver {
		final Object source;
		final NoiseBiomes biomes;

		Resolver(Object source, NoiseBiomes biomes) {
			this.source = source;
			this.biomes = biomes;
		}
	}

	protected final Mc mc;

	protected WorldgenApi(Mc mc) {
		this.mc = mc;
	}

	// ---- resources and registries

	/**
	 * The resources of the built-in data pack of the loaded Minecraft.
	 */
	Object createResourceManager() {
		Object pack = createVanillaPack();
		Object serverData = mc.get(PACK_TYPE, "SERVER_DATA");
		if (mc.hasType(MULTI_PACK_RESOURCE_MANAGER)) {
			return mc.create(MULTI_PACK_RESOURCE_MANAGER, serverData, Collections.singletonList(pack));
		}
		// 1.18 and 1.18.1
		Object result = mc.create(SIMPLE_RESOURCE_MANAGER, serverData);
		mc.call(result, "add", pack);
		return result;
	}

	private Object createVanillaPack() {
		if (mc.hasMethod(SERVER_PACKS_SOURCE, "createVanillaPackSource")) {
			// Public since 1.21
			Object pack = mc.callStatic(SERVER_PACKS_SOURCE, "createVanillaPackSource");
			// Since 26.3, the vanilla pack is split into its resources
			return mc.canCall(pack, "fullResources") ? mc.call(pack, "fullResources") : pack;
		}
		if (mc.hasField(SERVER_PACKS_SOURCE, "BUILT_IN_METADATA")) {
			// 1.18 to 1.19.2
			return mc.create(VANILLA_PACK_RESOURCES, mc.get(SERVER_PACKS_SOURCE, "BUILT_IN_METADATA"), new String[] { "minecraft" });
		}
		// 1.19.3 to 1.20.6 keep the method to themselves
		return mc.callPrivateStatic(SERVER_PACKS_SOURCE, "createVanillaPackSource");
	}

	/**
	 * The registries of the world generation, loaded from the built-in data
	 * pack with their tags, like for a new world of the server.
	 */
	abstract Object loadRegistryAccess(Object resourceManager);

	/**
	 * The keys of all biomes, like minecraft:plains.
	 */
	abstract List<String> biomeKeys(Object registryAccess);

	/**
	 * The key of a biome as the biome sampling returns it.
	 */
	abstract String biomeKey(Object registryAccess, Object biome);

	/**
	 * The key of a registry, whose constants moved to their own class in
	 * 1.19.3.
	 */
	Object registryKey(String name) {
		if (mc.hasType(REGISTRIES)) {
			return mc.get(REGISTRIES, name);
		}
		return mc.get(REGISTRY, name.equals("NOISE_SETTINGS") ? "NOISE_GENERATOR_SETTINGS_REGISTRY" : name + "_REGISTRY");
	}

	Object registry(Object registryAccess, String name) {
		Object key = registryKey(name);
		if (mc.canCall(registryAccess, "registryOrThrow")) {
			return mc.call(registryAccess, "registryOrThrow", key);
		}
		return mc.call(registryAccess, "lookupOrThrow", key);
	}

	/**
	 * The registry as a lookup of holders, which is what newer versions ask
	 * for and what a registry is itself since 1.21.2.
	 */
	Object asLookup(Object registry) {
		return mc.canCall(registry, "asLookup") ? mc.call(registry, "asLookup") : registry;
	}

	Object holderOrThrow(Object registry, Object key) {
		if (mc.canCall(registry, "getHolderOrThrow")) {
			return mc.call(registry, "getHolderOrThrow", key);
		}
		return mc.call(registry, "getOrThrow", key);
	}

	Stream<?> elements(Object registry) {
		if (mc.canCall(registry, "listElements")) {
			return (Stream<?>) mc.call(registry, "listElements");
		}
		return (Stream<?>) mc.call(registry, "holders");
	}

	/**
	 * The key of a holder, like minecraft:plains.
	 */
	String keyOf(Object holder) {
		Optional<?> key = (Optional<?>) mc.call(holder, "unwrapKey");
		return key.map(this::identifierOf).orElse("unregistered");
	}

	String identifierOf(Object resourceKey) {
		// 26.x renamed ResourceLocation to Identifier
		return mc.call(resourceKey, mc.canCall(resourceKey, "identifier") ? "identifier" : "location").toString();
	}

	// ---- dimensions

	/**
	 * The dimensions of a new world with the given seed, by the names of
	 * their level stems.
	 */
	abstract Map<String, Stem> createStems(Object registryAccess, WorldType worldType, long seed);

	/**
	 * The dimensions of the versions before world presets, which made the
	 * overworld generator by hand and took the other dimensions from the
	 * defaults of the dimension types.
	 */
	Map<String, Stem> createStemsFromDefaults(Object registryAccess, WorldType worldType, long seed) {
		Map<String, Stem> result = new HashMap<>();
		Object dimensions = mc.callStatic(DIMENSION_TYPE, "defaultDimensions", registryAccess, seed);
		Object nether = mc.call(dimensions, "get", mc.get(LEVEL_STEM, NETHER));
		if (nether != null) {
			result.put(NETHER, stemOf(nether));
		}
		Object overworldType = mc.call(registry(registryAccess, "DIMENSION_TYPE"), "getOrThrow", mc.get(DIMENSION_TYPE, "OVERWORLD_LOCATION"));
		result.put(OVERWORLD, stem(createOverworldGenerator(registryAccess, worldType, seed), overworldType));
		return result;
	}

	private Object createOverworldGenerator(Object registryAccess, WorldType worldType, long seed) {
		switch (worldType) {
		case FLAT:
			Object biomes = registry(registryAccess, "BIOME");
			if (mc.hasConstructor(FLAT_LEVEL_SOURCE, 2)) {
				Object structureSets = registry(registryAccess, "STRUCTURE_SET");
				return mc.create(FLAT_LEVEL_SOURCE, structureSets, mc.callStatic(FLAT_SETTINGS, "getDefault", biomes, structureSets));
			}
			return mc.create(FLAT_LEVEL_SOURCE, mc.callStatic(FLAT_SETTINGS, "getDefault", biomes));
		case LARGE_BIOMES:
			return mc.callStatic(WORLD_GEN_SETTINGS, "makeOverworld", registryAccess, seed, mc.get(NOISE_GENERATOR_SETTINGS, "LARGE_BIOMES"));
		case AMPLIFIED:
			return mc.callStatic(WORLD_GEN_SETTINGS, "makeOverworld", registryAccess, seed, mc.get(NOISE_GENERATOR_SETTINGS, "AMPLIFIED"));
		default:
			return mc.callStatic(WORLD_GEN_SETTINGS, "makeDefaultOverworld", registryAccess, seed);
		}
	}

	/**
	 * The stem of a level, whose dimension type was a value, then a
	 * supplier and then a holder.
	 */
	Stem stemOf(Object levelStem) {
		Object type = mc.call(levelStem, mc.canCall(levelStem, "typeHolder") ? "typeHolder" : "type");
		if (mc.hasType(HOLDER) && mc.type(HOLDER).isInstance(type)) {
			type = mc.call(type, "value");
		}
		return stem(mc.call(levelStem, "generator"), type);
	}

	Stem stem(Object generator, Object dimensionType) {
		return new Stem(generator, (Integer) mc.call(dimensionType, "minY"), (Integer) mc.call(dimensionType, "height"));
	}

	/**
	 * The noise of a dimension, which Minecraft keeps apart from the
	 * generator since 1.19. Null before that.
	 */
	Object createRandomState(ModernDimension dimension) {
		return null;
	}

	/**
	 * The structure placement state of a dimension, which Minecraft keeps
	 * apart from the generator since 1.19.3. Null before that.
	 */
	Object createStructureState(ModernDimension dimension) {
		return null;
	}

	/**
	 * The sampler of the climate of a dimension, for the thread that asks.
	 */
	abstract Object createClimateSampler(ModernDimension dimension);

	abstract List<StructureSet> readStructureSets(ModernDimension dimension);

	// ---- biomes

	/**
	 * The biomes of a dimension at quarter resolution, for the thread that
	 * asks.
	 */
	abstract Resolver quarterBiomes(ModernDimension dimension);

	/**
	 * The biomes of a dimension at the resolution of blocks, zoomed with
	 * the given seed from the given quarter resolution biomes.
	 */
	abstract NoiseBiomes blockBiomes(ModernDimension dimension, Resolver quarter, long biomeZoomSeed);

	/**
	 * The chunk that Minecraft starts its search for the world spawn from.
	 */
	abstract int[] originChunk(ModernDimension dimension);

	/**
	 * Samples the biome source of a dimension with its climate sampler, as
	 * every version before 26.2 does.
	 */
	Resolver samplerBiomes(ModernDimension dimension) {
		MethodHandle getNoiseBiome = mc.handle(BIOME_SOURCE, "getNoiseBiome", int.class, int.class, int.class, mc.type(CLIMATE_SAMPLER));
		Object biomeSource = dimension.getBiomeSource();
		Object sampler = dimension.getClimateSampler();
		return new Resolver(null, (x, y, z) -> getNoiseBiome.invoke(biomeSource, x, y, z, sampler));
	}

	/**
	 * Zooms biomes with the biome manager of the versions before 26.3,
	 * which asks a noise biome source for the quarter resolution biomes and
	 * answers for block positions.
	 */
	NoiseBiomes managerBlockBiomes(Resolver quarter, long biomeZoomSeed) {
		Object source = quarter.source != null && mc.type(NOISE_BIOME_SOURCE).isInstance(quarter.source)
				? quarter.source
				: mc.proxy(NOISE_BIOME_SOURCE, (proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return invokeObjectMethod(proxy, method, args);
					}
					return quarter.biomes.get((Integer) args[0], (Integer) args[1], (Integer) args[2]);
				});
		Object manager = mc.create(BIOME_MANAGER, source, biomeZoomSeed);
		MethodHandle newBlockPos = mc.constructorHandle(BLOCK_POS, int.class, int.class, int.class);
		MethodHandle getBiome = mc.handle(BIOME_MANAGER, "getBiome", mc.type(BLOCK_POS));
		return (x, y, z) -> getBiome.invoke(manager, newBlockPos.invoke(x, y, z));
	}

	private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
		switch (method.getName()) {
		case "hashCode":
			return System.identityHashCode(proxy);
		case "equals":
			return proxy == args[0];
		default:
			return "amidst noise biome source";
		}
	}

	/**
	 * The chunk of the position that the climate sampler suggests for the
	 * spawn, where Minecraft starts its search before 26.3.
	 */
	int[] spawnChunk(Object climateSampler) {
		int[] position = blockXZ(mc.call(climateSampler, "findSpawnPosition"));
		return new int[] { position[0] >> 4, position[1] >> 4 };
	}

	// ---- structures

	/**
	 * The chunk of the region that may hold a structure of the set, for the
	 * sets that spread their structures over regions.
	 */
	abstract int[] potentialChunk(ModernDimension dimension, StructureSet set, int chunkX, int chunkZ);

	/**
	 * The chunks of the sets that place their structures on rings around
	 * the origin of the world.
	 */
	abstract List<int[]> ringChunks(ModernDimension dimension, StructureSet set);

	/**
	 * The block position that /locate reports for a structure of the set
	 * that starts in the given chunk.
	 */
	abstract int[] locatePos(ModernDimension dimension, StructureSet set, Object chunkPos);

	/**
	 * The structure of the set that starts in the given chunk, or null if
	 * none does. Does what the chunk generator does to create the structures
	 * of a chunk, but without building the pieces of the structure.
	 */
	abstract StructureEntry findStart(ModernDimension dimension, StructureSet set, Object chunkPos, int chunkX, int chunkZ);

	// ---- templates

	/**
	 * The manager of the structure templates, which the start pieces of
	 * jigsaw structures like villages are placed from.
	 */
	Object createTemplateManager(Object resourceManager) {
		try {
			Path directory = Files.createTempDirectory("amidst-structures");
			directory.toFile().deleteOnExit();
			Object storage = mc.callStatic(LEVEL_STORAGE_SOURCE, "createDefault", directory);
			Object access = mc.call(storage, "createAccess", "amidst");
			Object fixer = mc.callStatic(DATA_FIXERS, "getDataFixer");
			if (!mc.hasType(STRUCTURE_TEMPLATE_MANAGER)) {
				// Before 1.19.3
				return mc.create(STRUCTURE_MANAGER, resourceManager, access, fixer);
			}
			if (mc.hasConstructor(STRUCTURE_TEMPLATE_MANAGER, 3)) {
				return mc.create(STRUCTURE_TEMPLATE_MANAGER, resourceManager, access, fixer);
			}
			return mc.create(STRUCTURE_TEMPLATE_MANAGER, resourceManager, access, fixer, asLookup(mc.get(BUILT_IN_REGISTRIES, "BLOCK")));
		} catch (IOException e) {
			throw new McException("unable to create a directory for structure templates", e);
		}
	}

	// ---- positions

	Object chunkPos(int chunkX, int chunkZ) {
		return mc.create(CHUNK_POS, chunkX, chunkZ);
	}

	/**
	 * The coordinates of a chunk position, which were fields before they
	 * became record components.
	 */
	int[] chunkXZ(Object chunkPos) {
		if (mc.canCall(chunkPos, "x")) {
			return new int[] { (Integer) mc.call(chunkPos, "x"), (Integer) mc.call(chunkPos, "z") };
		}
		return new int[] { (Integer) mc.get(chunkPos, "x"), (Integer) mc.get(chunkPos, "z") };
	}

	int[] blockXZ(Object blockPos) {
		return new int[] { (Integer) mc.call(blockPos, "getX"), (Integer) mc.call(blockPos, "getZ") };
	}
}
