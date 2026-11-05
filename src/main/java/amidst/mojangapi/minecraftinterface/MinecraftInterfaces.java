package amidst.mojangapi.minecraftinterface;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;

import amidst.clazz.Classes;
import amidst.clazz.real.JarFileParsingException;
import amidst.clazz.symbolic.SymbolicClass;
import amidst.clazz.symbolic.SymbolicClassGraphCreationException;
import amidst.clazz.translator.ClassTranslator;
import amidst.documentation.NotNull;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.file.LauncherProfile;
import amidst.mojangapi.file.service.MappedJarService;
import amidst.mojangapi.minecraftinterface.legacy.BetaClassTranslator;
import amidst.mojangapi.minecraftinterface.legacy.BetaMinecraftInterface;
import amidst.mojangapi.minecraftinterface.legacy.LegacyClassTranslator;
import amidst.mojangapi.minecraftinterface.legacy.LegacyMinecraftInterface;
import amidst.mojangapi.minecraftinterface.legacy._1_13ClassTranslator;
import amidst.mojangapi.minecraftinterface.legacy._1_13MinecraftInterface;
import amidst.mojangapi.minecraftinterface.legacy._1_15ClassTranslator;
import amidst.mojangapi.minecraftinterface.legacy._1_15MinecraftInterface;
import amidst.mojangapi.minecraftinterface.local.DefaultClassTranslator;
import amidst.mojangapi.minecraftinterface.local.LocalMinecraftInterface;
import amidst.mojangapi.minecraftinterface.modern.ModernMinecraftInterface;

public enum MinecraftInterfaces {
    ;

    @NotNull
    public static MinecraftInterface fromLocalProfile(LauncherProfile launcherProfile)
            throws MinecraftInterfaceCreationException {
        try {
            URLClassLoader classLoader = launcherProfile.newClassLoader();
            RecognisedVersion recognisedVersion = RecognisedVersion.from(classLoader);
            if (RecognisedVersion.isUnobfuscated(classLoader)) {
                classLoader.close();
                MinecraftInterface minecraftInterface = ModernMinecraftInterface
                        .create(launcherProfile.newIsolatedClassLoader(), recognisedVersion);
                AmidstLogger.info("Minecraft load complete.");
                return minecraftInterface;
            }
            if (RecognisedVersion.hasModernWorldGeneration(classLoader)) {
                // 1.18 to 1.21.11 are obfuscated, so a copy with readable names is loaded instead
                classLoader.close();
                Path mappedJar = getMappedJar(launcherProfile);
                MinecraftInterface minecraftInterface = ModernMinecraftInterface
                        .create(launcherProfile.newIsolatedClassLoader(mappedJar), recognisedVersion);
                AmidstLogger.info("Minecraft load complete.");
                return minecraftInterface;
            }
            Factory factory = fromVersion(recognisedVersion);
            Map<String, SymbolicClass> symbolicClassMap = Classes
                    .createSymbolicClassMap(launcherProfile.getJar(), classLoader, factory.classTranslator);
            MinecraftInterface minecraftInterface = factory.factory.apply(symbolicClassMap, recognisedVersion);

            AmidstLogger.info("Minecraft load complete.");
            return minecraftInterface;
        } catch (
                ClassNotFoundException
                | JarFileParsingException
                | SymbolicClassGraphCreationException
                | IOException e) {
            throw new MinecraftInterfaceCreationException("Unable to create local minecraft interface", e);
        } catch (UnsupportedClassVersionError e) {
        	throw new MinecraftInterfaceCreationException("This version of Minecraft isn't compatible with your Java installation", e);
        }
    }

    @NotNull
    private static Path getMappedJar(LauncherProfile launcherProfile) throws MinecraftInterfaceCreationException {
        try {
            return new MappedJarService().getMappedJar(launcherProfile);
        } catch (IOException e) {
            throw new MinecraftInterfaceCreationException(
                    "Unable to give this Minecraft version readable names. Amidst downloads the obfuscation map"
                            + " that Mojang publishes for it, so it needs an internet connection the first time.", e);
        }
    }

    @NotNull
    public static ClassTranslator getClassTranslatorFromVersion(RecognisedVersion recognisedVersion) {
        return fromVersion(recognisedVersion).classTranslator;
    }

    @NotNull
    private static Factory fromVersion(RecognisedVersion version) {
        if(RecognisedVersion.isOlderOrEqualTo(version, BetaMinecraftInterface.LAST_COMPATIBLE_VERSION)) {
            return new Factory(BetaClassTranslator.get(version), BetaMinecraftInterface::new);
        } else if(RecognisedVersion.isOlderOrEqualTo(version, LegacyMinecraftInterface.LAST_COMPATIBLE_VERSION)) {
            return new Factory(LegacyClassTranslator.get(), LegacyMinecraftInterface::new);
        } else if(RecognisedVersion.isOlderOrEqualTo(version, _1_13MinecraftInterface.LAST_COMPATIBLE_VERSION)) {
            return new Factory(_1_13ClassTranslator.get(), _1_13MinecraftInterface::new);
        } else if(RecognisedVersion.isOlderOrEqualTo(version, _1_15MinecraftInterface.LAST_COMPATIBLE_VERSION)) {
        	return new Factory(_1_15ClassTranslator.get(), _1_15MinecraftInterface::new);
        } else {
        	return new Factory(DefaultClassTranslator.get(), LocalMinecraftInterface::new);
        }
    }

    private static class Factory {
        public ClassTranslator classTranslator;
        public BiFunction<Map<String, SymbolicClass>, RecognisedVersion, MinecraftInterface> factory;

        public Factory(ClassTranslator classTranslator,
                BiFunction<Map<String, SymbolicClass>, RecognisedVersion, MinecraftInterface> factory) {
            this.classTranslator = classTranslator;
            this.factory = factory;
        }
    }
}
