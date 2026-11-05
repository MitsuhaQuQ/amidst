package amidst.mojangapi.minecraftinterface.modern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import amidst.documentation.ThreadSafe;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.minecraftinterface.modern.Mc.McException;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.PlacementKind;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureEntry;
import amidst.mojangapi.minecraftinterface.modern.ModernDimension.StructureSet;
import amidst.mojangapi.world.WorldType;
import amidst.mojangapi.world.icon.type.DefaultWorldIconTypes;

/**
 * The world generation of Minecraft 1.18 and 1.18.1, the two versions with
 * multi-noise biomes that still placed every kind of structure by the
 * spacing, separation and salt of its own settings, and strongholds on
 * rings the chunk generator computed itself.
 */
@ThreadSafe
class StructureFeatureWorldgen extends WorldgenApi {
	private static final String REGISTRY_ACCESS = "net.minecraft.core.RegistryAccess";
	private static final String CHUNK_GENERATOR = "net.minecraft.world.level.chunk.ChunkGenerator";
	private static final String STRUCTURE_FEATURES = "net.minecraft.data.worldgen.StructureFeatures";
	private static final String STRONGHOLD_KEY = "minecraft:stronghold";

	private volatile Object biomeRegistry;

	StructureFeatureWorldgen(Mc mc) {
		super(mc);
	}

	// ---- registries

	@Override
	Object loadRegistryAccess(Object resourceManager) {
		Object result = mc.callStatic(REGISTRY_ACCESS, "builtin");
		biomeRegistry = registry(result, "BIOME");
		return result;
	}

	@Override
	List<String> biomeKeys(Object registryAccess) {
		List<String> result = new ArrayList<>();
		for (Object key : (Set<?>) mc.call(registry(registryAccess, "BIOME"), "keySet")) {
			result.add(key.toString());
		}
		Collections.sort(result);
		return result;
	}

	@Override
	String biomeKey(Object registryAccess, Object biome) {
		return String.valueOf(mc.call(biomeRegistry, "getKey", biome));
	}

	// ---- dimensions

	@Override
	Map<String, Stem> createStems(Object registryAccess, WorldType worldType, long seed) {
		return createStemsFromDefaults(registryAccess, worldType, seed);
	}

	@Override
	Object createClimateSampler(ModernDimension dimension) {
		return mc.call(dimension.getGenerator(), "climateSampler");
	}

	/**
	 * Every kind of structure with settings in the generator becomes a set
	 * of its own, with the configured variants of the structure as its
	 * entries, like the chunk generator tries them.
	 */
	@Override
	List<StructureSet> readStructureSets(ModernDimension dimension) {
		List<StructureSet> result = new ArrayList<>();
		Object settings = mc.call(dimension.getGenerator(), "getSettings");
		Object features = mc.get(REGISTRY, "STRUCTURE_FEATURE");
		for (Object feature : (Iterable<?>) features) {
			Object config = mc.call(settings, "getConfig", feature);
			if (config == null) {
				continue;
			}
			String key = String.valueOf(mc.call(features, "getKey", feature));
			DefaultWorldIconTypes type = ModernStructures.iconOf(key);
			EnumSet<DefaultWorldIconTypes> types = type == null ? EnumSet.noneOf(DefaultWorldIconTypes.class) : EnumSet.of(type);
			List<StructureEntry> entries = new ArrayList<>();
			Map<?, ?> variants = (Map<?, ?>) mc.call(mc.call(settings, "structures", feature), "asMap");
			for (Map.Entry<?, ?> variant : variants.entrySet()) {
				entries.add(new StructureEntry(variant.getKey(), key, 1, type, variant.getValue()));
			}
			PlacementKind kind = PlacementKind.RANDOM_SPREAD;
			if (key.equals(STRONGHOLD_KEY)) {
				// The chunk generator places the stronghold itself, in the biomes its own check allows
				kind = PlacementKind.CONCENTRIC_RINGS;
				entries.clear();
				entries.add(new StructureEntry(mc.get(STRUCTURE_FEATURES, "STRONGHOLD"), key, 1, type, null));
			}
			result.add(new StructureSet(key, feature, config, kind, (Integer) mc.call(config, "spacing"), entries, types));
		}
		return result;
	}

	// ---- biomes

	@Override
	Resolver quarterBiomes(ModernDimension dimension) {
		return samplerBiomes(dimension);
	}

	@Override
	NoiseBiomes blockBiomes(ModernDimension dimension, Resolver quarter, long biomeZoomSeed) {
		return managerBlockBiomes(quarter, biomeZoomSeed);
	}

	@Override
	int[] originChunk(ModernDimension dimension) {
		return spawnChunk(dimension.getClimateSampler());
	}

	// ---- structures

	@Override
	int[] potentialChunk(ModernDimension dimension, StructureSet set, int chunkX, int chunkZ) {
		return chunkXZ(mc.call(set.placement, "getPotentialFeatureChunk", set.extra, dimension.getSeed(), chunkX, chunkZ));
	}

	/**
	 * The generator places the strongholds when it is first asked about
	 * one, and keeps their chunks to itself.
	 */
	@Override
	List<int[]> ringChunks(ModernDimension dimension, StructureSet set) {
		Object generator = dimension.getGenerator();
		mc.call(generator, "hasStronghold", chunkPos(0, 0));
		List<int[]> result = new ArrayList<>();
		try {
			for (Object chunkPos : (List<?>) mc.getPrivate(generator, "strongholdPositions")) {
				result.add(chunkXZ(chunkPos));
			}
		} catch (McException e) {
			AmidstLogger.warn(e, "Unable to read where Minecraft places the strongholds");
		}
		return result;
	}

	@Override
	int[] locatePos(ModernDimension dimension, StructureSet set, Object chunkPos) {
		return blockXZ(mc.call(set.placement, "getLocatePos", chunkPos));
	}

	/**
	 * Does what ChunkGenerator.createStructures does for one kind of
	 * structure: tries its configured variants in order, each in the biomes
	 * it is registered for, until one can start in the chunk.
	 */
	@Override
	StructureEntry findStart(ModernDimension dimension, StructureSet set, Object chunkPos, int chunkX, int chunkZ) {
		if (set.kind == PlacementKind.CONCENTRIC_RINGS) {
			if (!(Boolean) mc.call(dimension.getGenerator(), "hasStronghold", chunkPos)) {
				return null;
			}
		} else {
			int[] potential = potentialChunk(dimension, set, chunkX, chunkZ);
			if (potential[0] != chunkX || potential[1] != chunkZ) {
				return null;
			}
		}
		for (StructureEntry entry : set.entries) {
			Collection<?> biomeKeys = (Collection<?>) entry.extra;
			Predicate<Object> validBiome = biomeKeys == null
					? biome -> (Boolean) mc.callPrivateStatic(CHUNK_GENERATOR, "validStrongholdBiome", biome)
					: biome -> {
						Optional<?> key = (Optional<?>) mc.call(biomeRegistry, "getResourceKey", biome);
						return key.isPresent() && biomeKeys.contains(key.get());
					};
			try {
				Object start = mc.call(entry.holder, "generate",
						dimension.getRegistryAccess(), dimension.getGenerator(), dimension.getBiomeSource(), dimension.getTemplateManager(),
						dimension.getSeed(), chunkPos, 0, set.extra, dimension.getHeightAccessor(), validBiome);
				if ((Boolean) mc.call(start, "isValid")) {
					return entry;
				}
			} catch (McException e) {
				dimension.reportFailure(entry.key, e);
			}
		}
		return null;
	}
}
