package amidst.mojangapi.file.json.versionlist;

import java.net.URL;

import com.google.gson.annotations.SerializedName;

import amidst.documentation.GsonConstructor;
import amidst.mojangapi.file.json.ReleaseType;

public class RemoteVersionJson {
	private volatile String id;
	private volatile ReleaseType type;
	private volatile Inner downloads;

	@GsonConstructor
	public RemoteVersionJson() {
	}

	public String getId() {
		return id;
	}

	public ReleaseType getType() {
		return type;
	}

	public URL getClientUrl() {
		return downloads.client == null ? null : downloads.client.url;
	}

	public URL getServerUrl() {
		return downloads.server == null ? null : downloads.server.url;
	}

	/**
	 * The obfuscation map of the client, which Mojang publishes since 1.14.4.
	 */
	public URL getClientMappingsUrl() {
		return downloads.clientMappings == null ? null : downloads.clientMappings.url;
	}

	private static class Inner {
		public volatile Download client;
		public volatile Download server;
		@SerializedName("client_mappings")
		public volatile Download clientMappings;

		@GsonConstructor
		public Inner() {
		}
	}

	private static class Download {
		public volatile URL url;

		@GsonConstructor
		public Download() {
		}
	}
}
