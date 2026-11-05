package amidst.gui.main;

import com.google.gson.annotations.SerializedName;

import amidst.documentation.GsonConstructor;
import amidst.documentation.Immutable;

/**
 * A release as the GitHub API lists it.
 */
@Immutable
public class GitHubReleaseJson {
	@SerializedName("tag_name")
	private volatile String tagName;
	private volatile String name;
	@SerializedName("html_url")
	private volatile String htmlUrl;
	private volatile boolean draft;
	private volatile boolean prerelease;

	@GsonConstructor
	public GitHubReleaseJson() {
	}

	public String getTagName() {
		return tagName;
	}

	public String getName() {
		return name;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	public boolean isDraft() {
		return draft;
	}

	public boolean isPrerelease() {
		return prerelease;
	}
}
