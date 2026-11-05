package amidst.mojangapi.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import amidst.documentation.Immutable;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.file.LauncherProfile;
import amidst.mojangapi.file.Version;
import amidst.mojangapi.file.json.version.DownloadJson;
import amidst.mojangapi.mapping.JarRemapper;
import amidst.mojangapi.mapping.ProguardMappings;
import amidst.parsing.FormatException;
import amidst.parsing.URIUtils;
import amidst.util.OperatingSystemDetector;

/**
 * Minecraft 1.18 to 1.21.11 ship with obfuscated names, but Mojang publishes
 * the obfuscation map of every version. This service fetches that map and
 * writes a copy of the jar with readable names, which Amidst then loads like
 * the readable versions since 26.1. The copies are kept in a cache directory
 * of the user, since writing one takes a few seconds.
 */
@Immutable
public class MappedJarService {
	private static final String MAPPINGS_FILE = "client.txt";
	private static final String MAPPED_SUFFIX = "-mapped.jar";
	private static final String STAMP_SUFFIX = "-mapped.sha1";

	private final Path cacheDirectory;

	public MappedJarService() {
		this(getDefaultCacheDirectory());
	}

	public MappedJarService(Path cacheDirectory) {
		this.cacheDirectory = cacheDirectory;
	}

	/**
	 * The copy of the jar of the profile with readable names, written if the
	 * cache has none for this jar yet.
	 */
	public Path getMappedJar(LauncherProfile profile) throws IOException {
		Path jar = profile.getJar();
		String id = profile.getVersionId();
		String name = toFileName(id);
		Path directory = cacheDirectory.resolve(name);
		Path mapped = directory.resolve(name + MAPPED_SUFFIX);
		Path stamp = directory.resolve(name + STAMP_SUFFIX);
		String sha1 = sha1Of(jar);
		if (Files.isRegularFile(mapped) && sha1.equals(readStamp(stamp))) {
			AmidstLogger.info("Using the copy of Minecraft {} with readable names at {}", id, mapped);
			return mapped;
		}
		Files.createDirectories(directory);
		Path mappings = getMappings(profile, directory);
		AmidstLogger.info("Giving Minecraft {} readable names, this takes a moment.", id);
		long start = System.currentTimeMillis();
		JarRemapper.remap(jar, ProguardMappings.read(mappings), mapped);
		Files.write(stamp, sha1.getBytes(StandardCharsets.UTF_8));
		AmidstLogger.info("Wrote {} in {} seconds.", mapped, (System.currentTimeMillis() - start) / 1000);
		return mapped;
	}

	private Path getMappings(LauncherProfile profile, Path directory) throws IOException {
		Path result = directory.resolve(MAPPINGS_FILE);
		if (Files.isRegularFile(result)) {
			return result;
		}
		Optional<DownloadJson> mappings = profile.getClientMappings();
		URL url = mappings.isPresent() ? mappings.get().getUrl() : findRemoteMappingsUrl(profile.getVersionId());
		AmidstLogger.info("Downloading the obfuscation map of Minecraft {} from {}", profile.getVersionId(), url);
		download(url, result);
		if (mappings.isPresent() && mappings.get().getSha1() != null && !mappings.get().getSha1().equalsIgnoreCase(sha1Of(result))) {
			Files.delete(result);
			throw new IOException("the downloaded obfuscation map of Minecraft " + profile.getVersionId() + " is damaged");
		}
		return result;
	}

	/**
	 * The json of a modded version does not name the map, so it is looked
	 * up in the version list of Mojang by the id.
	 */
	private static URL findRemoteMappingsUrl(String versionId) throws IOException {
		try {
			for (Version version : Version.newRemoteVersionList()) {
				if (version.getId().equals(versionId)) {
					URL result = version.fetchRemoteVersion().getClientMappingsUrl();
					if (result != null) {
						return result;
					}
					break;
				}
			}
		} catch (FormatException e) {
			throw new IOException("unable to read the version list of Mojang", e);
		}
		throw new IOException("Mojang publishes no obfuscation map for the Minecraft version " + versionId);
	}

	private static void download(URL url, Path target) throws IOException {
		Path part = target.resolveSibling(target.getFileName() + ".part");
		try (InputStream in = URIUtils.newInputStream(url)) {
			Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String readStamp(Path stamp) {
		try {
			return Files.isRegularFile(stamp) ? new String(Files.readAllBytes(stamp), StandardCharsets.UTF_8).trim() : "";
		} catch (IOException e) {
			return "";
		}
	}

	private static String sha1Of(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			try (InputStream in = Files.newInputStream(file)) {
				byte[] buffer = new byte[1 << 16];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					digest.update(buffer, 0, read);
				}
			}
			StringBuilder result = new StringBuilder();
			for (byte b : digest.digest()) {
				result.append(String.format("%02x", b));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-1 is not available", e);
		}
	}

	/**
	 * A version id as a file name; ids of modded versions can hold any
	 * character.
	 */
	private static String toFileName(String versionId) {
		return versionId.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	/**
	 * The cache directory of the user, where the copies can be deleted at
	 * any time and Amidst writes them again.
	 */
	public static Path getDefaultCacheDirectory() {
		Path home = Paths.get(System.getProperty("user.home", "."));
		if (OperatingSystemDetector.isWindows()) {
			String localAppData = System.getenv("LOCALAPPDATA");
			if (localAppData != null && Files.isDirectory(Paths.get(localAppData))) {
				return Paths.get(localAppData, "Amidst", "mapped-jars");
			}
		} else if (OperatingSystemDetector.isMac()) {
			return home.resolve("Library/Caches/Amidst/mapped-jars");
		} else {
			String xdgCache = System.getenv("XDG_CACHE_HOME");
			Path cache = xdgCache != null && !xdgCache.isEmpty() ? Paths.get(xdgCache) : home.resolve(".cache");
			return cache.resolve("amidst/mapped-jars");
		}
		return home.resolve(".amidst/mapped-jars");
	}
}
