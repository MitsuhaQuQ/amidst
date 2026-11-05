package amidst.mojangapi.file;

import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Optional;

import amidst.documentation.Immutable;
import amidst.mojangapi.file.directory.DotMinecraftDirectory;
import amidst.mojangapi.file.directory.ProfileDirectory;
import amidst.mojangapi.file.directory.VersionDirectory;
import amidst.mojangapi.file.json.version.DownloadJson;
import amidst.mojangapi.file.json.version.DownloadsJson;
import amidst.mojangapi.file.json.version.VersionJson;
import amidst.mojangapi.file.service.ClassLoaderService;

@Immutable
public class LauncherProfile {
	private final ClassLoaderService classLoaderService = new ClassLoaderService();
	private final DotMinecraftDirectory dotMinecraftDirectory;
	private final ProfileDirectory profileDirectory;
	private final VersionDirectory versionDirectory;
	private final VersionJson versionJson;
	private final boolean isVersionListedInProfile;
	private final String profileName;

	public LauncherProfile(
			DotMinecraftDirectory dotMinecraftDirectory,
			ProfileDirectory profileDirectory,
			VersionDirectory versionDirectory,
			VersionJson versionJson,
			boolean isVersionListedInProfile,
			String profileName) {
		this.dotMinecraftDirectory = dotMinecraftDirectory;
		this.profileDirectory = profileDirectory;
		this.versionDirectory = versionDirectory;
		this.versionJson = versionJson;
		this.isVersionListedInProfile = isVersionListedInProfile;
		this.profileName = profileName;
	}

	public String getVersionId() {
		return versionJson.getId();
	}

	/**
	 * True, iff the contained version was listed in the profile. Especially,
	 * this is false if a modded profiles was resolved via
	 * {@link UnresolvedLauncherProfile#resolveToVanilla(VersionList)}.
	 */
	public boolean isVersionListedInProfile() {
		return isVersionListedInProfile;
	}

	public String getVersionName() {
		return getVersionNamePrefix() + versionJson.getId();
	}

	private String getVersionNamePrefix() {
		if (isVersionListedInProfile) {
			return "";
		} else {
			return "*";
		}
	}

	public String getProfileName() {
		return profileName;
	}

	public Path getJar() {
		return versionDirectory.getJar();
	}

	/**
	 * The obfuscation map that Mojang publishes for the version, if the
	 * version json names it.
	 */
	public Optional<DownloadJson> getClientMappings() {
		DownloadsJson downloads = versionJson.getDownloads();
		if (downloads == null || downloads.getClientMappings() == null || downloads.getClientMappings().getUrl() == null) {
			return Optional.empty();
		}
		return Optional.of(downloads.getClientMappings());
	}

	public Path getSaves() {
		return profileDirectory.getSaves();
	}

	public URLClassLoader newClassLoader() throws MalformedURLException {
		return classLoaderService.createClassLoader(
				dotMinecraftDirectory.getLibraries(),
				versionJson.getLibraries(),
				versionDirectory.getJar());
	}

	/**
	 * A class loader in which Minecraft sees its own libraries rather than
	 * those of Amidst.
	 */
	public URLClassLoader newIsolatedClassLoader() throws MalformedURLException {
		return newIsolatedClassLoader(versionDirectory.getJar());
	}

	/**
	 * Like {@link #newIsolatedClassLoader()}, but with another jar in place
	 * of the one of the version, such as a copy with readable names.
	 */
	public URLClassLoader newIsolatedClassLoader(Path jar) throws MalformedURLException {
		return classLoaderService.createIsolatedClassLoader(
				dotMinecraftDirectory.getLibraries(),
				versionJson.getLibraries(),
				jar);
	}
}
