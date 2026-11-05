package amidst.gui.main;

import java.io.IOException;

import amidst.documentation.Immutable;
import amidst.documentation.NotNull;
import amidst.parsing.FormatException;
import amidst.parsing.json.JsonReader;

/**
 * Asks GitHub for the releases of this fork and picks the newest one of the
 * line this build belongs to. GitHub lists the newest release first.
 */
@Immutable
public enum UpdateInformationRetriever {
	;

	public static final String PROJECT_URL = "https://github.com/MitsuhaQuQ/amidst";
	public static final String RELEASES_URL = PROJECT_URL + "/releases";
	public static final String ISSUES_URL = PROJECT_URL + "/issues";
	private static final String RELEASES_API_URL = "https://api.github.com/repos/MitsuhaQuQ/amidst/releases?per_page=30";
	/**
	 * The line of releases this build belongs to, named by the suffix of the
	 * tags, like v4.8-mc1.18. A tag without a suffix counts as well.
	 */
	private static final String RELEASE_LINE = "mc";

	@NotNull
	public static UpdateInformation retrieve() throws FormatException, IOException {
		GitHubReleaseJson[] releases = JsonReader.readLocation(RELEASES_API_URL, GitHubReleaseJson[].class);
		for (GitHubReleaseJson release : releases) {
			if (release.isDraft()) {
				continue;
			}
			ReleaseTag tag = ReleaseTag.parse(release.getTagName());
			if (tag != null && (tag.isLine(RELEASE_LINE) || tag.isLine(""))) {
				return new UpdateInformation(tag.getVersion(), tag.getTag(), release.getName(), release.getHtmlUrl());
			}
		}
		throw new FormatException("GitHub lists no release of Amidst for Minecraft");
	}
}
