package amidst.mojangapi.minecraftinterface.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import amidst.documentation.ThreadSafe;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.minecraftinterface.modern.Mc.McException;
import amidst.mojangapi.world.icon.type.DefaultWorldIconTypes;

/**
 * The generator of one dimension of a world, as Minecraft sets it up for a
 * new world with the given seed. The calls into Minecraft that differ
 * between versions go through the {@link WorldgenApi}.
 */
@ThreadSafe
public class ModernDimension {
	private static final String LEVEL_HEIGHT_ACCESSOR = "net.minecraft.world.level.LevelHeightAccessor";

	/**
	 * How a structure set places its structures.
	 */
	enum PlacementKind {
		/** One structure per region of spacing times spacing chunks. */
		RANDOM_SPREAD,
		/** On rings around the origin of the world, like strongholds. */
		CONCENTRIC_RINGS,
		/** A kind of placement Amidst does not know, so every chunk is asked about. */
		OTHER
	}

	/**
	 * A structure set, or until 1.18.1 a kind of structure with its
	 * settings.
	 */
	static class StructureSet {
		final String key;
		/** The placement, or until 1.18.1 the structure feature. */
		final Object placement;
		/** Until 1.18.1, the spacing, separation and salt of the feature. */
		final Object extra;
		final PlacementKind kind;
		final int spacing;
		final List<StructureEntry> entries;
		final Set<DefaultWorldIconTypes> types;

		StructureSet(String key, Object placement, Object extra, PlacementKind kind, int spacing, List<StructureEntry> entries, Set<DefaultWorldIconTypes> types) {
			this.key = key;
			this.placement = placement;
			this.extra = extra;
			this.kind = kind;
			this.spacing = spacing;
			this.entries = Collections.unmodifiableList(entries);
			this.types = Collections.unmodifiableSet(types);
		}

		@Override
		public String toString() {
			return key;
		}
	}

	/**
	 * A structure of a set.
	 */
	static class StructureEntry {
		/** The holder of the structure, or until 1.18.1 the configured structure feature. */
		final Object holder;
		final String key;
		final int weight;
		final DefaultWorldIconTypes type;
		/** Until 1.18.1, the keys of the biomes the variant is registered for. */
		final Object extra;

		StructureEntry(Object holder, String key, int weight, DefaultWorldIconTypes type, Object extra) {
			this.holder = holder;
			this.key = key;
			this.weight = weight;
			this.type = type;
			this.extra = extra;
		}
	}

	private final WorldgenApi api;
	private final Object registryAccess;
	private final Supplier<Object> templateManager;
	private final long seed;
	private final Object generator;
	private final Object biomeSource;
	private final Object heightAccessor;
	private final Object randomState;
	private final Object structureState;
	private final List<StructureSet> structureSets;
	private final ThreadLocal<Object> climateSampler;
	private final Set<String> failedStructures = ConcurrentHashMap.newKeySet();
	private final Map<StructureSet, List<int[]>> ringChunks = new ConcurrentHashMap<>();

	ModernDimension(Mc mc, WorldgenApi api, Object registryAccess, WorldgenApi.Stem stem, long seed, Supplier<Object> templateManager) {
		this.api = api;
		this.registryAccess = registryAccess;
		this.templateManager = templateManager;
		this.seed = seed;
		this.generator = stem.generator;
		this.biomeSource = mc.call(generator, "getBiomeSource");
		this.heightAccessor = mc.callStatic(LEVEL_HEIGHT_ACCESSOR, "create", stem.minY, stem.height);
		this.randomState = api.createRandomState(this);
		this.climateSampler = ThreadLocal.withInitial(() -> api.createClimateSampler(this));
		this.structureState = api.createStructureState(this);
		this.structureSets = Collections.unmodifiableList(api.readStructureSets(this));
	}

	Object getRegistryAccess() {
		return registryAccess;
	}

	Object getTemplateManager() {
		return templateManager.get();
	}

	public long getSeed() {
		return seed;
	}

	Object getGenerator() {
		return generator;
	}

	Object getBiomeSource() {
		return biomeSource;
	}

	Object getHeightAccessor() {
		return heightAccessor;
	}

	/** Null before 1.19. */
	Object getRandomState() {
		return randomState;
	}

	/** Null before 1.19.3. */
	Object getStructureState() {
		return structureState;
	}

	/** The climate sampler of the calling thread. */
	Object getClimateSampler() {
		return climateSampler.get();
	}

	/**
	 * The biomes at quarter resolution, for the calling thread.
	 */
	WorldgenApi.Resolver createQuarterBiomes() {
		return api.quarterBiomes(this);
	}

	WorldgenApi.NoiseBiomes createBlockBiomes(WorldgenApi.Resolver quarter, long biomeZoomSeed) {
		return api.blockBiomes(this, quarter, biomeZoomSeed);
	}

	/**
	 * The chunk that Minecraft starts its search for the world spawn from.
	 */
	public int[] getOriginChunk() {
		return api.originChunk(this);
	}

	/**
	 * Whether this dimension places any structure of the given type.
	 */
	public boolean hasStructures(DefaultWorldIconTypes type) {
		for (StructureSet set : structureSets) {
			if (set.types.contains(type)) {
				return true;
			}
		}
		return false;
	}

	public List<ConcentricRings> getConcentricRings(Set<DefaultWorldIconTypes> types) {
		List<ConcentricRings> result = new ArrayList<>();
		for (StructureSet set : structureSets) {
			if (set.kind == PlacementKind.CONCENTRIC_RINGS && !Collections.disjoint(set.types, types)) {
				result.add(new ConcentricRings(set, getRingChunks(set)));
			}
		}
		return result;
	}

	/**
	 * Minecraft searches for biomes to place the rings, so the chunks are only
	 * computed once.
	 */
	private List<int[]> getRingChunks(StructureSet set) {
		return ringChunks.computeIfAbsent(set, s -> Collections.unmodifiableList(api.ringChunks(this, s)));
	}

	/**
	 * Finds the structures of the given types whose start chunk is in the
	 * given square of chunks. The coordinates passed to the consumer are those
	 * that /locate reports.
	 */
	public void findStructures(
			int chunkX,
			int chunkZ,
			int size,
			Set<DefaultWorldIconTypes> types,
			StructureConsumer consumer) {
		for (StructureSet set : structureSets) {
			if (Collections.disjoint(set.types, types)) {
				continue;
			}
			for (int[] chunk : getCandidateChunks(set, chunkX, chunkZ, size)) {
				findStructure(set, chunk[0], chunk[1], types, consumer);
			}
		}
	}

	public void findStructure(ConcentricRings rings, int[] chunk, Set<DefaultWorldIconTypes> types, StructureConsumer consumer) {
		findStructure(rings.set, chunk[0], chunk[1], types, consumer);
	}

	private List<int[]> getCandidateChunks(StructureSet set, int chunkX, int chunkZ, int size) {
		List<int[]> result = new ArrayList<>();
		switch (set.kind) {
		case RANDOM_SPREAD:
			int spacing = set.spacing;
			for (int regionX = Math.floorDiv(chunkX, spacing); regionX <= Math.floorDiv(chunkX + size - 1, spacing); regionX++) {
				for (int regionZ = Math.floorDiv(chunkZ, spacing); regionZ <= Math.floorDiv(chunkZ + size - 1, spacing); regionZ++) {
					int[] chunk = api.potentialChunk(this, set, regionX * spacing, regionZ * spacing);
					if (isInSquare(chunk[0], chunk[1], chunkX, chunkZ, size)) {
						result.add(chunk);
					}
				}
			}
			break;
		case CONCENTRIC_RINGS:
			for (int[] chunk : getRingChunks(set)) {
				if (isInSquare(chunk[0], chunk[1], chunkX, chunkZ, size)) {
					result.add(chunk);
				}
			}
			break;
		default:
			for (int x = chunkX; x < chunkX + size; x++) {
				for (int z = chunkZ; z < chunkZ + size; z++) {
					result.add(new int[] { x, z });
				}
			}
		}
		return result;
	}

	private static boolean isInSquare(int x, int z, int chunkX, int chunkZ, int size) {
		return x >= chunkX && x < chunkX + size && z >= chunkZ && z < chunkZ + size;
	}

	private void findStructure(StructureSet set, int chunkX, int chunkZ, Set<DefaultWorldIconTypes> types, StructureConsumer consumer) {
		Object chunkPos = api.chunkPos(chunkX, chunkZ);
		StructureEntry found;
		try {
			found = api.findStart(this, set, chunkPos, chunkX, chunkZ);
		} catch (McException e) {
			reportFailure(set.key, e);
			return;
		}
		if (found != null && found.type != null && types.contains(found.type)) {
			int[] position = api.locatePos(this, set, chunkPos);
			consumer.accept(found.type, position[0], position[1]);
		}
	}

	/**
	 * Logs once per structure that Minecraft could not be asked about it,
	 * so the other structures still show.
	 *
	 * @return false, for the callers that answer whether a structure is there
	 */
	boolean reportFailure(String key, McException e) {
		if (failedStructures.add(key)) {
			AmidstLogger.warn(e, "Unable to check where Minecraft places {}", key);
		}
		return false;
	}

	@FunctionalInterface
	public interface StructureConsumer {
		void accept(DefaultWorldIconTypes type, int blockX, int blockZ);
	}

	public static class ConcentricRings {
		private final StructureSet set;
		private final List<int[]> chunks;

		private ConcentricRings(StructureSet set, List<int[]> chunks) {
			this.set = set;
			this.chunks = Collections.unmodifiableList(chunks);
		}

		public List<int[]> getChunks() {
			return chunks;
		}
	}
}
