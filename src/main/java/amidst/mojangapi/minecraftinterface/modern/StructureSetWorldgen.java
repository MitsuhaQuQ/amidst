package amidst.mojangapi.minecraftinterface.modern;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amidst.documentation.ThreadSafe;
import amidst.mojangapi.minecraftinterface.modern.Mc.McException;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.PlacementKind;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureEntry;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureSet;
import amidst.mojangapi.world.WorldType;
import amidst.mojangapi.world.icon.type.DefaultWorldIconTypes;

/**
 * The world generation of Minecraft 1.18.2 and later, which places
 * structures by structure sets. Within that, the registries got layers in
 * 1.19.3, the noise moved out of the generator in 1.19, the structure
 * placement moved out of the generator in 1.19.3, tags are bound while the
 * registries load since 1.21.2, and 26.3 gave the biome source resolvers of
 * its own.
 */
@ThreadSafe
class StructureSetWorldgen extends WorldgenApi {
	static final String STRUCTURE_SET = "net.minecraft.world.level.levelgen.structure.StructureSet";
	private static final String RANDOM_SPREAD = "net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement";
	private static final String CONCENTRIC_RINGS = "net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement";
	private static final String STRUCTURE_FEATURE = "net.minecraft.world.level.levelgen.feature.StructureFeature";
	private static final String GENERATION_CONTEXT = "net.minecraft.world.level.levelgen.structure.Structure$GenerationContext";
	private static final String NOISE_BASED_GENERATOR = "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator";
	private static final String RANDOM_STATE = "net.minecraft.world.level.levelgen.RandomState";
	private static final String STRUCTURE_STATE = "net.minecraft.world.level.chunk.ChunkGeneratorStructureState";
	private static final String SAMPLER_CONTEXT = "net.minecraft.world.level.levelgen.densityfunction.SamplerContext";
	private static final String NOISE_BIOME_RESOLVER = "net.minecraft.world.level.biome.NoiseBiomeResolver";
	private static final String BIOME_RESOLVER = "net.minecraft.world.level.biome.BiomeResolver";
	private static final String CHUNK_GENERATOR = "net.minecraft.world.level.chunk.ChunkGenerator";
	private static final String WORLD_PRESETS = "net.minecraft.world.level.levelgen.presets.WorldPresets";
	private static final String REGISTRY_ACCESS = "net.minecraft.core.RegistryAccess";
	private static final String REGISTRY_LAYER = "net.minecraft.server.RegistryLayer";
	private static final String REGISTRY_DATA_LOADER = "net.minecraft.resources.RegistryDataLoader";
	private static final String TAG_LOADER = "net.minecraft.tags.TagLoader";
	private static final String TAG_MANAGER = "net.minecraft.tags.TagManager";
	private static final String TAG_KEY = "net.minecraft.tags.TagKey";
	private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
	private static final String WORLDGEN_RANDOM = "net.minecraft.world.level.levelgen.WorldgenRandom";
	private static final String LEGACY_RANDOM_SOURCE = "net.minecraft.world.level.levelgen.LegacyRandomSource";
	private static final Executor SAME_THREAD = Runnable::run;
	private static final int QUART_SHIFT = 2;

	/** Since 1.19, the noise of a dimension is a RandomState. */
	private final boolean hasRandomState;
	/** Since 1.19.3, the structure placement of a dimension is a ChunkGeneratorStructureState. */
	private final boolean hasStructureState;
	/** Since 26.3, the density functions are sampled through a SamplerContext. */
	private final boolean hasSamplerContext;

	StructureSetWorldgen(Mc mc) {
		super(mc);
		this.hasRandomState = mc.hasType(RANDOM_STATE);
		this.hasStructureState = mc.hasType(STRUCTURE_STATE);
		this.hasSamplerContext = mc.hasType(SAMPLER_CONTEXT);
	}

	// ---- registries

	@Override
	Object loadRegistryAccess(Object resourceManager) {
		if (!mc.hasType(REGISTRY_LAYER)) {
			// 1.18.2 to 1.19.2: the world generation is still built into the code
			Object writable = mc.callStatic(REGISTRY_ACCESS, "builtinCopy");
			Object frozen = mc.call(writable, "freeze");
			bindTags(frozen, resourceManager);
			return frozen;
		}
		Object layers = mc.callStatic(REGISTRY_LAYER, "createRegistryAccess");
		// 26.3 renamed the worldgen layer and its registries
		Object worldLayer = mc.get(REGISTRY_LAYER, mc.hasField(REGISTRY_LAYER, "WORLD") ? "WORLD" : "WORLDGEN");
		Object registries = mc.get(REGISTRY_DATA_LOADER, mc.hasField(REGISTRY_DATA_LOADER, "WORLD_REGISTRIES") ? "WORLD_REGISTRIES" : "WORLDGEN_REGISTRIES");
		Object loading = mc.call(layers, "getAccessForLoading", worldLayer);
		Object loaded;
		if (mc.hasMethod(TAG_LOADER, "loadTagsForExistingRegistries")) {
			// Since 1.21.2, the tags are bound while the registries load
			Object staticTags = mc.callStatic(TAG_LOADER, "loadTagsForExistingRegistries",
					resourceManager, mc.call(layers, "getLayer", mc.get(REGISTRY_LAYER, "STATIC")));
			Object context = mc.callStatic(TAG_LOADER, "buildUpdatedLookups", loading, staticTags);
			if (mc.hasMethod(REGISTRY_DATA_LOADER, "load", 3)) {
				loaded = mc.callStatic(REGISTRY_DATA_LOADER, "load", resourceManager, context, registries);
			} else {
				// 26.1 and later load in the background
				loaded = ((CompletableFuture<?>) mc.callStatic(REGISTRY_DATA_LOADER, "load", resourceManager, context, registries, SAME_THREAD)).join();
			}
		} else {
			loaded = mc.callStatic(REGISTRY_DATA_LOADER, "load", resourceManager, loading, registries);
		}
		Object result = mc.call(mc.call(layers, "replaceFrom", worldLayer, Collections.singletonList(loaded)), "compositeAccess");
		if (!mc.hasMethod(TAG_LOADER, "loadTagsForExistingRegistries")) {
			bindTags(result, resourceManager);
		}
		return result;
	}

	/**
	 * Binds the tags of the data pack to the registries, like the tag
	 * manager of the server does when its resources load. The biome tags
	 * say where structures may be placed.
	 */
	private void bindTags(Object registryAccess, Object resourceManager) {
		boolean hasTagsDirPath = mc.hasType(REGISTRIES) && mc.hasMethod(REGISTRIES, "tagsDirPath");
		List<?> entries = ((Stream<?>) mc.call(registryAccess, "registries")).collect(Collectors.toList());
		for (Object entry : entries) {
			Object key = mc.call(entry, "key");
			Object registry = mc.call(entry, "value");
			String directory = (String) (hasTagsDirPath
					? mc.callStatic(REGISTRIES, "tagsDirPath", key)
					: mc.callStatic(TAG_MANAGER, "getTagDir", key));
			Function<Object, Optional<?>> lookup = id -> (Optional<?>) mc.call(registry, "getHolder", mc.callStatic(RESOURCE_KEY, "create", key, id));
			Object loader = mc.create(TAG_LOADER, lookup, directory);
			Map<?, ?> tags = (Map<?, ?>) mc.call(loader, "loadAndBuild", resourceManager);
			Map<Object, List<Object>> bound = new HashMap<>();
			tags.forEach((id, tag) -> {
				// Until 1.18.2, the values of a tag are wrapped in a Tag
				Collection<?> values = tag instanceof Collection ? (Collection<?>) tag : (Collection<?>) mc.call(tag, "getValues");
				bound.put(mc.callStatic(TAG_KEY, "create", key, id), new ArrayList<>(values));
			});
			mc.call(registry, "bindTags", bound);
		}
	}

	@Override
	List<String> biomeKeys(Object registryAccess) {
		List<String> result = new ArrayList<>();
		elements(registry(registryAccess, "BIOME")).forEach(holder -> result.add(keyOf(holder)));
		Collections.sort(result);
		return result;
	}

	@Override
	String biomeKey(Object registryAccess, Object biome) {
		return keyOf(biome);
	}

	// ---- dimensions

	@Override
	Map<String, Stem> createStems(Object registryAccess, WorldType worldType, long seed) {
		if (!mc.hasType(WORLD_PRESETS)) {
			return createStemsFromDefaults(registryAccess, worldType, seed);
		}
		Object presets = registry(registryAccess, "WORLD_PRESET");
		Object preset = mc.call(holderOrThrow(presets, mc.get(WORLD_PRESETS, presetName(worldType))), "value");
		Function<String, Object> stemOf;
		if (mc.canCall(preset, "createWorldDimensions")) {
			// Since 1.19.3
			Object dimensions = mc.call(preset, "createWorldDimensions");
			stemOf = name -> ((Optional<?>) mc.call(dimensions, "get", mc.get(LEVEL_STEM, name))).orElse(null);
		} else {
			// 1.19 to 1.19.2
			Object settings = mc.call(preset, "createWorldGenSettings", seed, true, false);
			Object dimensions = mc.call(settings, "dimensions");
			stemOf = name -> mc.call(dimensions, "get", mc.get(LEVEL_STEM, name));
		}
		Map<String, Stem> result = new HashMap<>();
		for (String name : new String[] { OVERWORLD, NETHER }) {
			Object stem = stemOf.apply(name);
			if (stem != null) {
				result.put(name, stemOf(stem));
			}
		}
		return result;
	}

	private static String presetName(WorldType worldType) {
		switch (worldType) {
		case FLAT:
			return "FLAT";
		case AMPLIFIED:
			return "AMPLIFIED";
		case LARGE_BIOMES:
			return "LARGE_BIOMES";
		default:
			return "NORMAL";
		}
	}

	/**
	 * Like ChunkMap does it for the generator of a level.
	 */
	@Override
	Object createRandomState(ModernDimension dimension) {
		if (!hasRandomState) {
			return null;
		}
		Object generator = dimension.getGenerator();
		long seed = dimension.getSeed();
		Object noises = asLookup(registry(dimension.getRegistryAccess(), "NOISE"));
		if (mc.hasMethod(RANDOM_STATE, "createForGenerator")) {
			// Since 26.4, Minecraft decides itself how a generator gets its noise
			return mc.callStatic(RANDOM_STATE, "createForGenerator", noises, seed, generator, dimension.getHeightAccessor());
		}
		if (mc.type(NOISE_BASED_GENERATOR).isInstance(generator)) {
			Object settings = mc.call(mc.call(generator, "generatorSettings"), "value");
			if (hasSamplerContext) {
				return mc.callStatic(RANDOM_STATE, "create", noises, seed, settings);
			}
			return mc.callStatic(RANDOM_STATE, "create", settings, noises, seed);
		}
		// Flat worlds have no noise of their own
		if (hasSamplerContext) {
			Object stone = mc.call(mc.get("net.minecraft.world.level.block.Blocks", "STONE"), "defaultBlockState");
			return mc.callStatic(RANDOM_STATE, "create",
					noises, seed, false, stone, 63, mc.callStatic("net.minecraft.world.level.levelgen.NoiseRouterData", "none"));
		}
		return mc.callStatic(RANDOM_STATE, "create", mc.callStatic(NOISE_GENERATOR_SETTINGS, "dummy"), noises, seed);
	}

	@Override
	Object createStructureState(ModernDimension dimension) {
		Object generator = dimension.getGenerator();
		if (hasStructureState) {
			Object structureSets = asLookup(registry(dimension.getRegistryAccess(), "STRUCTURE_SET"));
			Object result = mc.call(generator, "createState", structureSets, dimension.getRandomState(), dimension.getSeed());
			mc.call(result, "ensureStructuresGenerated");
			return result;
		}
		if (hasRandomState) {
			mc.call(generator, "ensureStructuresGenerated", dimension.getRandomState());
		} else {
			mc.call(generator, "ensureStructuresGenerated");
		}
		return null;
	}

	@Override
	Object createClimateSampler(ModernDimension dimension) {
		if (hasSamplerContext) {
			Object context = mc.call(mc.call(mc.callStatic(SAMPLER_CONTEXT, "builder"), "enableCaches"), "build");
			return mc.call(dimension.getRandomState(), "createClimateSampler", context);
		}
		if (hasRandomState) {
			return mc.call(dimension.getRandomState(), "sampler");
		}
		return mc.call(dimension.getGenerator(), "climateSampler");
	}

	@Override
	List<StructureSet> readStructureSets(ModernDimension dimension) {
		List<StructureSet> result = new ArrayList<>();
		List<?> holders;
		if (hasStructureState) {
			holders = (List<?>) mc.call(dimension.getStructureState(), "possibleStructureSets");
		} else {
			holders = ((Stream<?>) mc.call(dimension.getGenerator(), "possibleStructureSets")).collect(Collectors.toList());
		}
		for (Object holder : holders) {
			Object set = mc.call(holder, "value");
			List<StructureEntry> entries = new ArrayList<>();
			EnumSet<DefaultWorldIconTypes> types = EnumSet.noneOf(DefaultWorldIconTypes.class);
			for (Object entry : (List<?>) mc.call(set, "structures")) {
				Object structure = mc.call(entry, "structure");
				String key = keyOf(structure);
				DefaultWorldIconTypes type = ModernStructures.iconOf(key);
				if (type != null) {
					types.add(type);
				}
				entries.add(new StructureEntry(structure, key, (Integer) mc.call(entry, "weight"), type, null));
			}
			Object placement = mc.call(set, "placement");
			PlacementKind kind = PlacementKind.OTHER;
			int spacing = 0;
			if (mc.type(RANDOM_SPREAD).isInstance(placement)) {
				kind = PlacementKind.RANDOM_SPREAD;
				spacing = (Integer) mc.call(placement, "spacing");
			} else if (mc.type(CONCENTRIC_RINGS).isInstance(placement)) {
				kind = PlacementKind.CONCENTRIC_RINGS;
			}
			result.add(new StructureSet(keyOf(holder), placement, null, kind, spacing, entries, types));
		}
		return result;
	}

	// ---- biomes

	@Override
	Resolver quarterBiomes(ModernDimension dimension) {
		if (!mc.hasMethod(BIOME_SOURCE, "createUncachedResolver")) {
			return samplerBiomes(dimension);
		}
		Object resolver = mc.call(dimension.getBiomeSource(), "createUncachedResolver", dimension.getRandomState());
		// 26.4 moved the noise biomes from BiomeResolver to NoiseBiomeResolver
		String type = mc.hasType(NOISE_BIOME_RESOLVER) ? NOISE_BIOME_RESOLVER : BIOME_RESOLVER;
		MethodHandle getNoiseBiome = mc.handle(type, "getNoiseBiome", int.class, int.class, int.class);
		return new Resolver(resolver, (x, y, z) -> getNoiseBiome.invoke(resolver, x, y, z));
	}

	@Override
	NoiseBiomes blockBiomes(ModernDimension dimension, Resolver quarter, long biomeZoomSeed) {
		if (quarter.source != null && mc.hasMethod(BIOME_MANAGER, "getBiome", 3)) {
			// Since 26.3, the biome manager zooms a resolver and answers for coordinates
			Object manager = mc.create(BIOME_MANAGER, quarter.source, biomeZoomSeed);
			MethodHandle getBiome = mc.handle(BIOME_MANAGER, "getBiome", int.class, int.class, int.class);
			return (x, y, z) -> getBiome.invoke(manager, x, y, z);
		}
		return managerBlockBiomes(quarter, biomeZoomSeed);
	}

	@Override
	int[] originChunk(ModernDimension dimension) {
		if (mc.hasMethod(CHUNK_GENERATOR, "getOrigin")) {
			return chunkXZ(mc.call(dimension.getGenerator(), "getOrigin", dimension.getRandomState()));
		}
		return spawnChunk(dimension.getClimateSampler());
	}

	// ---- structures

	private boolean isStructureChunk(ModernDimension dimension, StructureSet set, int chunkX, int chunkZ) {
		if (hasStructureState) {
			return (Boolean) mc.call(set.placement, "isStructureChunk", dimension.getStructureState(), chunkX, chunkZ);
		}
		if (hasRandomState) {
			return (Boolean) mc.call(set.placement, "isStructureChunk", dimension.getGenerator(), dimension.getRandomState(), dimension.getSeed(), chunkX, chunkZ);
		}
		return (Boolean) mc.call(set.placement, "isFeatureChunk", dimension.getGenerator(), dimension.getSeed(), chunkX, chunkZ);
	}

	@Override
	int[] potentialChunk(ModernDimension dimension, StructureSet set, int chunkX, int chunkZ) {
		String method = mc.canCall(set.placement, "getPotentialStructureChunk") ? "getPotentialStructureChunk" : "getPotentialFeatureChunk";
		return chunkXZ(mc.call(set.placement, method, dimension.getSeed(), chunkX, chunkZ));
	}

	@Override
	List<int[]> ringChunks(ModernDimension dimension, StructureSet set) {
		List<?> positions;
		if (hasStructureState) {
			positions = (List<?>) mc.call(dimension.getStructureState(), "getRingPositionsFor", set.placement);
		} else if (hasRandomState) {
			positions = (List<?>) mc.call(dimension.getGenerator(), "getRingPositionsFor", set.placement, dimension.getRandomState());
		} else {
			positions = (List<?>) mc.call(dimension.getGenerator(), "getRingPositionsFor", set.placement);
		}
		List<int[]> result = new ArrayList<>();
		for (Object chunkPos : positions == null ? Collections.emptyList() : positions) {
			result.add(chunkXZ(chunkPos));
		}
		return result;
	}

	@Override
	int[] locatePos(ModernDimension dimension, StructureSet set, Object chunkPos) {
		if (mc.canCall(set.placement, "getLocatePos")) {
			return blockXZ(mc.call(set.placement, "getLocatePos", chunkPos));
		}
		// 1.18.2
		if (set.kind == PlacementKind.RANDOM_SPREAD) {
			return blockXZ(mc.callStatic(STRUCTURE_FEATURE, "getLocatePos", set.placement, chunkPos));
		}
		return blockXZ(mc.call(chunkPos, "getWorldPosition"));
	}

	/**
	 * Does what ChunkGenerator.tryGenerateStructure does: picks the
	 * structures of the set by their weights until one can start in the
	 * chunk.
	 */
	@Override
	StructureEntry findStart(ModernDimension dimension, StructureSet set, Object chunkPos, int chunkX, int chunkZ) {
		if (!isStructureChunk(dimension, set, chunkX, chunkZ)) {
			return null;
		}
		if (set.entries.size() == 1) {
			return isValidStart(dimension, set.entries.get(0), chunkPos) ? set.entries.get(0) : null;
		}
		List<StructureEntry> options = new ArrayList<>(set.entries);
		Object random = mc.create(WORLDGEN_RANDOM, mc.create(LEGACY_RANDOM_SOURCE, 0L));
		mc.call(random, "setLargeFeatureSeed", dimension.getSeed(), chunkX, chunkZ);
		int total = options.stream().mapToInt(o -> o.weight).sum();
		while (!options.isEmpty()) {
			int choice = (Integer) mc.call(random, "nextInt", total);
			int index = 0;
			for (StructureEntry option : options) {
				choice -= option.weight;
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureEntry selected = options.get(index);
			if (isValidStart(dimension, selected, chunkPos)) {
				return selected;
			}
			options.remove(index);
			total -= selected.weight;
		}
		return null;
	}

	private boolean isValidStart(ModernDimension dimension, StructureEntry entry, Object chunkPos) {
		try {
			Object structure = mc.call(entry.holder, "value");
			Object biomes = mc.call(structure, "biomes");
			Predicate<Object> validBiome = biome -> (Boolean) mc.call(biomes, "contains", biome);
			if (!hasRandomState) {
				// 1.18.2 only tells after building the pieces
				Object start = mc.call(structure, "generate",
						dimension.getRegistryAccess(), dimension.getGenerator(), dimension.getBiomeSource(), dimension.getTemplateManager(),
						dimension.getSeed(), chunkPos, 0, dimension.getHeightAccessor(), validBiome);
				return (Boolean) mc.call(start, "isValid");
			}
			Object context;
			if (hasSamplerContext) {
				context = mc.create(GENERATION_CONTEXT,
						dimension.getRegistryAccess(), dimension.getGenerator(), dimension.getBiomeSource(), dimension.getClimateSampler(),
						dimension.getRandomState(), dimension.getTemplateManager(), dimension.getSeed(), chunkPos, dimension.getHeightAccessor(), validBiome);
			} else {
				context = mc.create(GENERATION_CONTEXT,
						dimension.getRegistryAccess(), dimension.getGenerator(), dimension.getBiomeSource(),
						dimension.getRandomState(), dimension.getTemplateManager(), dimension.getSeed(), chunkPos, dimension.getHeightAccessor(), validBiome);
			}
			if (mc.canCall(structure, "findValidGenerationPoint")) {
				return ((Optional<?>) mc.call(structure, "findValidGenerationPoint", context)).isPresent();
			}
			// 1.19 to 1.19.2 check the biome of the found point in Structure.generate
			Optional<?> stub = (Optional<?>) mc.call(structure, "findGenerationPoint", context);
			if (!stub.isPresent()) {
				return false;
			}
			Object position = mc.call(stub.get(), "position");
			Object biome = mc.call(dimension.getBiomeSource(), "getNoiseBiome",
					(Integer) mc.call(position, "getX") >> QUART_SHIFT,
					(Integer) mc.call(position, "getY") >> QUART_SHIFT,
					(Integer) mc.call(position, "getZ") >> QUART_SHIFT,
					dimension.getClimateSampler());
			return validBiome.test(biome);
		} catch (McException e) {
			return dimension.reportFailure(entry.key, e);
		}
	}
}
