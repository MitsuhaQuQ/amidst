package amidst.gui.main;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import amidst.AmidstVersion;
import amidst.documentation.Immutable;

/**
 * The tag of a release of this fork, like v4.8-mc1.18: the version of
 * Amidst, then the line of releases the tag belongs to, named by the
 * letters after the dash. The releases for MTM Worldgen are tagged
 * v4.7-mtm.2 and so on, so a build of one line is not offered the releases
 * of the other.
 */
@Immutable
public class ReleaseTag {
	private static final Pattern PATTERN = Pattern.compile("v(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([A-Za-z]*).*)?");

	/**
	 * @return the parsed tag, or null if the tag is not one of a release of
	 *         Amidst
	 */
	public static ReleaseTag parse(String tag) {
		if (tag == null) {
			return null;
		}
		Matcher matcher = PATTERN.matcher(tag.trim());
		if (!matcher.matches()) {
			return null;
		}
		AmidstVersion version = new AmidstVersion(
				Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)),
				matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)));
		return new ReleaseTag(tag.trim(), version, matcher.group(4) == null ? "" : matcher.group(4));
	}

	private final String tag;
	private final AmidstVersion version;
	private final String line;

	private ReleaseTag(String tag, AmidstVersion version, String line) {
		this.tag = tag;
		this.version = version;
		this.line = line;
	}

	public String getTag() {
		return tag;
	}

	public AmidstVersion getVersion() {
		return version;
	}

	/**
	 * The line of releases, like mc, or an empty string for a tag like v4.7
	 * without one.
	 */
	public String getLine() {
		return line;
	}

	public boolean isLine(String line) {
		return this.line.equals(line);
	}
}
