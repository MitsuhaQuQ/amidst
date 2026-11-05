package amidst.gui.main;

import org.junit.Assert;
import org.junit.Test;

import amidst.AmidstVersion;

public class ReleaseTagTest {
	@Test
	public void shouldParseTheVersionAndTheLine() {
		ReleaseTag tag = ReleaseTag.parse("v4.8-mc1.18");
		Assert.assertEquals("v4.8", tag.getVersion().createVersionString());
		Assert.assertEquals("mc", tag.getLine());
		Assert.assertTrue(tag.isLine("mc"));
		Assert.assertFalse(tag.isLine("mtm"));
	}

	@Test
	public void shouldParseTagsOfOtherLinesAndWithoutALine() {
		Assert.assertEquals("mtm", ReleaseTag.parse("v4.7-mtm.2").getLine());
		Assert.assertEquals("mc", ReleaseTag.parse("v4.7-mc26.1").getLine());
		ReleaseTag plain = ReleaseTag.parse("v4.7");
		Assert.assertEquals("", plain.getLine());
		Assert.assertEquals("v4.7", plain.getVersion().createVersionString());
		Assert.assertEquals("v4.7.1", ReleaseTag.parse("v4.7.1-mc26.2").getVersion().createVersionString());
	}

	@Test
	public void shouldRejectOtherTags() {
		Assert.assertNull(ReleaseTag.parse(null));
		Assert.assertNull(ReleaseTag.parse("release-1"));
		Assert.assertNull(ReleaseTag.parse("4.7"));
	}

	@Test
	public void shouldCompareLikeAmidstVersions() {
		AmidstVersion current = new AmidstVersion(4, 7, 0);
		Assert.assertTrue(ReleaseTag.parse("v4.8-mc1.18").getVersion().isNewerMinorVersionThan(current));
		Assert.assertFalse(ReleaseTag.parse("v4.7-mc26.1").getVersion().isNewerMinorVersionThan(current));
	}
}
