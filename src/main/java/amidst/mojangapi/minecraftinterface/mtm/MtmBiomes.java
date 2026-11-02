package amidst.mojangapi.minecraftinterface.mtm;

import static amidst.mojangapi.world.biome.BiomeType.BEACH;
import static amidst.mojangapi.world.biome.BiomeType.BEACH_CLIFFS;
import static amidst.mojangapi.world.biome.BiomeType.DEEP_OCEAN;
import static amidst.mojangapi.world.biome.BiomeType.HILLS;
import static amidst.mojangapi.world.biome.BiomeType.ISLAND;
import static amidst.mojangapi.world.biome.BiomeType.MOUNTAINS;
import static amidst.mojangapi.world.biome.BiomeType.OCEAN;
import static amidst.mojangapi.world.biome.BiomeType.PLAINS;
import static amidst.mojangapi.world.biome.BiomeType.PLAINS_FLAT;
import static amidst.mojangapi.world.biome.BiomeType.PLAINS_TAIGA;
import static amidst.mojangapi.world.biome.BiomeType.PLATEAU;
import static amidst.mojangapi.world.biome.BiomeType.RIVER;
import static amidst.mojangapi.world.biome.BiomeType.SWAMPLAND;

import java.util.LinkedHashMap;
import java.util.Map;

import amidst.documentation.Immutable;
import amidst.mojangapi.world.biome.Biome;
import amidst.mojangapi.world.biome.BiomeList;
import amidst.mojangapi.world.biome.BiomeType;

/**
 * The biomes of Minecraft 1.18 to 1.20, by resource id. Biomes that existed before 1.18 keep their old numeric ids,
 * so the biome checks of the structure producers and the colors of existing biome profiles still apply to them.
 * Newer biomes use the ids of cubiomes.
 */
@Immutable
public enum MtmBiomes {
	;

	public static final int UNKNOWN_ID = -1;

	private static final Map<String, Biome> BY_RESOURCE_ID = new LinkedHashMap<>();

	// @formatter:off
	static {
		add("ocean",                    0,   "Ocean",                    OCEAN);
		add("plains",                   1,   "Plains",                   PLAINS);
		add("desert",                   2,   "Desert",                   PLAINS_FLAT);
		add("windswept_hills",          3,   "Windswept Hills",          MOUNTAINS);
		add("forest",                   4,   "Forest",                   PLAINS);
		add("taiga",                    5,   "Taiga",                    PLAINS_TAIGA);
		add("swamp",                    6,   "Swamp",                    SWAMPLAND);
		add("river",                    7,   "River",                    RIVER);
		add("nether_wastes",            8,   "Nether Wastes",            PLAINS);
		add("the_end",                  9,   "The End",                  PLAINS);
		add("frozen_ocean",             10,  "Frozen Ocean",             OCEAN);
		add("frozen_river",             11,  "Frozen River",             RIVER);
		add("snowy_plains",             12,  "Snowy Plains",             PLAINS_FLAT);
		add("mushroom_fields",          14,  "Mushroom Fields",          ISLAND);
		add("beach",                    16,  "Beach",                    BEACH);
		add("jungle",                   21,  "Jungle",                   PLAINS);
		add("sparse_jungle",            23,  "Sparse Jungle",            PLAINS);
		add("deep_ocean",               24,  "Deep Ocean",               DEEP_OCEAN);
		add("stony_shore",              25,  "Stony Shore",              BEACH_CLIFFS);
		add("snowy_beach",              26,  "Snowy Beach",              BEACH);
		add("birch_forest",             27,  "Birch Forest",             PLAINS);
		add("dark_forest",              29,  "Dark Forest",              PLAINS);
		add("snowy_taiga",              30,  "Snowy Taiga",              PLAINS_TAIGA);
		add("old_growth_pine_taiga",    32,  "Old Growth Pine Taiga",    PLAINS_TAIGA);
		add("windswept_forest",         34,  "Windswept Forest",         MOUNTAINS);
		add("savanna",                  35,  "Savanna",                  PLAINS_FLAT);
		add("savanna_plateau",          36,  "Savanna Plateau",          PLATEAU);
		add("badlands",                 37,  "Badlands",                 PLAINS);
		add("wooded_badlands",          38,  "Wooded Badlands",          PLATEAU);
		add("small_end_islands",        40,  "Small End Islands",        PLAINS);
		add("end_midlands",             41,  "End Midlands",             PLAINS);
		add("end_highlands",            42,  "End Highlands",            PLAINS);
		add("end_barrens",              43,  "End Barrens",              PLAINS);
		add("warm_ocean",               44,  "Warm Ocean",               OCEAN);
		add("lukewarm_ocean",           45,  "Lukewarm Ocean",           OCEAN);
		add("cold_ocean",               46,  "Cold Ocean",               OCEAN);
		add("deep_lukewarm_ocean",      48,  "Deep Lukewarm Ocean",      OCEAN);
		add("deep_cold_ocean",          49,  "Deep Cold Ocean",          OCEAN);
		add("deep_frozen_ocean",        50,  "Deep Frozen Ocean",        OCEAN);
		add("the_void",                 127, "The Void",                 PLAINS);
		add("sunflower_plains",         129, "Sunflower Plains",         PLAINS);
		add("windswept_gravelly_hills", 131, "Windswept Gravelly Hills", MOUNTAINS);
		add("flower_forest",            132, "Flower Forest",            PLAINS);
		add("ice_spikes",               140, "Ice Spikes",               PLAINS_FLAT);
		add("old_growth_birch_forest",  155, "Old Growth Birch Forest",  PLAINS);
		add("old_growth_spruce_taiga",  160, "Old Growth Spruce Taiga",  PLAINS_TAIGA);
		add("windswept_savanna",        163, "Windswept Savanna",        PLAINS_FLAT);
		add("eroded_badlands",          165, "Eroded Badlands",          PLAINS);
		add("bamboo_jungle",            168, "Bamboo Jungle",            PLAINS);
		add("soul_sand_valley",         170, "Soul Sand Valley",         PLAINS);
		add("crimson_forest",           171, "Crimson Forest",           PLAINS);
		add("warped_forest",            172, "Warped Forest",            PLAINS);
		add("basalt_deltas",            173, "Basalt Deltas",            PLAINS);
		add("dripstone_caves",          174, "Dripstone Caves",          PLAINS);
		add("lush_caves",               175, "Lush Caves",               PLAINS);
		add("meadow",                   177, "Meadow",                   PLATEAU);
		add("grove",                    178, "Grove",                    HILLS);
		add("snowy_slopes",             179, "Snowy Slopes",             HILLS);
		add("jagged_peaks",             180, "Jagged Peaks",             MOUNTAINS);
		add("frozen_peaks",             181, "Frozen Peaks",             MOUNTAINS);
		add("stony_peaks",              182, "Stony Peaks",              MOUNTAINS);
		add("deep_dark",                183, "Deep Dark",                PLAINS);
		add("mangrove_swamp",           184, "Mangrove Swamp",           SWAMPLAND);
		add("cherry_grove",             185, "Cherry Grove",             PLATEAU);
	}
	// @formatter:on

	public static final BiomeList BIOME_LIST = BiomeList.construct(new BiomeList(BY_RESOURCE_ID.values()));

	private static void add(String name, int id, String displayName, BiomeType type) {
		BY_RESOURCE_ID.put("minecraft:" + name, new Biome(id, displayName, type));
	}

	/** Numeric id of a biome resource id such as {@code minecraft:meadow}, or {@link #UNKNOWN_ID}. */
	public static int idOf(String resourceId) {
		Biome biome = BY_RESOURCE_ID.get(resourceId);
		return biome == null ? UNKNOWN_ID : biome.getId();
	}
}
