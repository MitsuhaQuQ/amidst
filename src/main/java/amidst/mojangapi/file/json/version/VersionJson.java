package amidst.mojangapi.file.json.version;

import java.util.Collections;
import java.util.List;

import amidst.documentation.GsonConstructor;
import amidst.documentation.Immutable;

@Immutable
public class VersionJson {
	private volatile String id;
	private volatile String inheritsFrom;
	private volatile List<LibraryJson> libraries = Collections.emptyList();
	private volatile DownloadsJson downloads;

	@GsonConstructor
	public VersionJson() {
	}

	public String getId() {
		return id;
	}

	public String getInheritsFrom() {
		return inheritsFrom;
	}

	public List<LibraryJson> getLibraries() {
		return libraries;
	}

	/**
	 * The files Mojang publishes for the version, or null for the json of a
	 * modded version.
	 */
	public DownloadsJson getDownloads() {
		return downloads;
	}
}
