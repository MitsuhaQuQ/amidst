package amidst.mojangapi.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProguardMappingsTest {
	private static final String SAMPLE = String.join("\n",
			"# {\"id\":\"sourceFile\"}",
			"net.minecraft.world.level.biome.BiomeManager -> dxq:",
			"# {\"fileName\":\"BiomeManager.java\",\"id\":\"sourceFile\"}",
			"    long biomeZoomSeed -> f",
			"    net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource noiseBiomeSource -> e",
			"    21:24:void <init>(net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource,long) -> <init>",
			"    30:31:net.minecraft.core.Holder getBiome(net.minecraft.core.BlockPos) -> a",
			"    40:41:int[] sample(java.lang.String[],int) -> b",
			"    50:55:void inlined(double):60:61 -> c",
			"    boolean plain() -> d",
			"net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource -> dxq$a:",
			"    net.minecraft.core.Holder getNoiseBiome(int,int,int) -> a",
			"net.minecraft.core.Holder -> jh:",
			"net.minecraft.core.BlockPos -> jd:");

	private ProguardMappings mappings;

	@Before
	public void setUp() throws IOException {
		mappings = ProguardMappings.parse(new BufferedReader(new StringReader(SAMPLE)));
	}

	@Test
	public void shouldMapClasses() {
		Assert.assertEquals(4, mappings.getClassCount());
		Assert.assertEquals("net/minecraft/world/level/biome/BiomeManager", mappings.mapClass("dxq"));
		Assert.assertEquals("net/minecraft/world/level/biome/BiomeManager$NoiseBiomeSource", mappings.mapClass("dxq$a"));
		Assert.assertEquals("java/lang/Object", mappings.mapClass("java/lang/Object"));
		Assert.assertTrue(mappings.hasClass("jh"));
		Assert.assertFalse(mappings.hasClass("zz"));
	}

	@Test
	public void shouldMapFieldsByObfuscatedDescriptor() {
		Assert.assertEquals("biomeZoomSeed", mappings.mapField("dxq", "f", "J"));
		Assert.assertEquals("noiseBiomeSource", mappings.mapField("dxq", "e", "Ldxq$a;"));
		Assert.assertNull(mappings.mapField("dxq", "zz", "I"));
		Assert.assertNull(mappings.mapField("unknown", "f", "J"));
	}

	@Test
	public void shouldMapMethodsByObfuscatedDescriptor() {
		Assert.assertEquals("getBiome", mappings.mapMethod("dxq", "a", "(Ljd;)Ljh;"));
		Assert.assertEquals("sample", mappings.mapMethod("dxq", "b", "([Ljava/lang/String;I)[I"));
		Assert.assertEquals("inlined", mappings.mapMethod("dxq", "c", "(D)V"));
		Assert.assertEquals("plain", mappings.mapMethod("dxq", "d", "()Z"));
		Assert.assertEquals("getNoiseBiome", mappings.mapMethod("dxq$a", "a", "(III)Ljh;"));
		// The same obfuscated name with another descriptor is another method
		Assert.assertNull(mappings.mapMethod("dxq", "a", "(I)V"));
	}

	@Test
	public void shouldWriteDescriptorsWithObfuscatedClassNames() {
		Map<String, String> classes = new HashMap<>();
		classes.put("net/minecraft/core/Holder", "jh");
		Assert.assertEquals("Ljh;", ProguardMappings.toDescriptor("net.minecraft.core.Holder", classes));
		Assert.assertEquals("[[Ljh;", ProguardMappings.toDescriptor("net.minecraft.core.Holder[][]", classes));
		Assert.assertEquals("Ljava/lang/String;", ProguardMappings.toDescriptor("java.lang.String", Collections.emptyMap()));
		Assert.assertEquals("[I", ProguardMappings.toDescriptor("int[]", Collections.emptyMap()));
		Assert.assertEquals("V", ProguardMappings.toDescriptor("void", Collections.emptyMap()));
	}

	@Test
	public void shouldFindInheritedMembersThroughTheHierarchy() {
		Map<String, MinecraftRemapper.ClassHeader> headers = new HashMap<>();
		headers.put("sub", new MinecraftRemapper.ClassHeader("dxq", new String[] { "dxq$a" }));
		headers.put("dxq", new MinecraftRemapper.ClassHeader("java/lang/Object", new String[0]));
		MinecraftRemapper remapper = new MinecraftRemapper(mappings, headers);
		Assert.assertEquals("net/minecraft/world/level/biome/BiomeManager", remapper.map("dxq"));
		Assert.assertEquals("getBiome", remapper.mapMethodName("sub", "a", "(Ljd;)Ljh;"));
		Assert.assertEquals("getNoiseBiome", remapper.mapMethodName("sub", "a", "(III)Ljh;"));
		Assert.assertEquals("biomeZoomSeed", remapper.mapFieldName("sub", "f", "J"));
		Assert.assertEquals("<init>", remapper.mapMethodName("sub", "<init>", "()V"));
		Assert.assertEquals("clone", remapper.mapMethodName("[Ljava/lang/Object;", "clone", "()Ljava/lang/Object;"));
		Assert.assertEquals("unknown", remapper.mapMethodName("sub", "unknown", "()V"));
	}
}
