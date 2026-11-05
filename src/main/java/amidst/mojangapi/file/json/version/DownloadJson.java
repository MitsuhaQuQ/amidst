package amidst.mojangapi.file.json.version;

import java.net.URL;

import amidst.documentation.GsonConstructor;
import amidst.documentation.Immutable;

@Immutable
public class DownloadJson {
	private volatile URL url;
	private volatile String sha1;
	private volatile long size;

	@GsonConstructor
	public DownloadJson() {
	}

	public URL getUrl() {
		return url;
	}

	public String getSha1() {
		return sha1;
	}

	public long getSize() {
		return size;
	}
}
