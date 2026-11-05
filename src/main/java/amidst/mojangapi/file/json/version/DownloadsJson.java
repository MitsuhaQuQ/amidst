package amidst.mojangapi.file.json.version;

import com.google.gson.annotations.SerializedName;

import amidst.documentation.GsonConstructor;
import amidst.documentation.Immutable;

/**
 * The files that Mojang publishes for a version, as the version json names
 * them.
 */
@Immutable
public class DownloadsJson {
	private volatile DownloadJson client;
	@SerializedName("client_mappings")
	private volatile DownloadJson clientMappings;

	@GsonConstructor
	public DownloadsJson() {
	}

	public DownloadJson getClient() {
		return client;
	}

	/**
	 * The obfuscation map of the client, which Mojang publishes since 1.14.4.
	 */
	public DownloadJson getClientMappings() {
		return clientMappings;
	}
}
