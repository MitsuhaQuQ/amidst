package amidst.gui.main;

import amidst.AmidstVersion;
import amidst.documentation.Immutable;

/**
 * The newest release that this build could be updated to.
 */
@Immutable
public class UpdateInformation {
	private final AmidstVersion version;
	private final String tag;
	private final String name;
	private final String downloadUrl;

	public UpdateInformation(AmidstVersion version, String tag, String name, String downloadUrl) {
		this.version = version;
		this.tag = tag;
		this.name = name;
		this.downloadUrl = downloadUrl;
	}

	public AmidstVersion getVersion() {
		return version;
	}

	public String getTag() {
		return tag;
	}

	/**
	 * The title of the release, shown with the prompt.
	 */
	public String getName() {
		return name;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}
}
