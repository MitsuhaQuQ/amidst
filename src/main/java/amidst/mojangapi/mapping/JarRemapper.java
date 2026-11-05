package amidst.mojangapi.mapping;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;

import amidst.documentation.Immutable;
import amidst.logging.AmidstLogger;

/**
 * Writes a copy of an obfuscated Minecraft jar in which the classes and
 * their members have the readable names of Mojang's obfuscation map. The
 * resources of the jar, such as the data pack, are copied as they are.
 */
@Immutable
public enum JarRemapper {
	;

	private static final String CLASS_SUFFIX = ".class";
	private static final String MANIFEST = "META-INF/MANIFEST.MF";
	private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

	public static void remap(Path obfuscatedJar, ProguardMappings mappings, Path readableJar) throws IOException {
		Path part = readableJar.resolveSibling(readableJar.getFileName() + ".part");
		Files.createDirectories(readableJar.toAbsolutePath().getParent());
		try (ZipFile in = new ZipFile(obfuscatedJar.toFile())) {
			MinecraftRemapper remapper = new MinecraftRemapper(mappings, readHeaders(in));
			try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(part)))) {
				writeManifest(out);
				Set<String> written = new HashSet<>();
				written.add(MANIFEST);
				Enumeration<? extends ZipEntry> entries = in.entries();
				int classCount = 0;
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (entry.isDirectory() || isSignature(name)) {
						continue;
					}
					byte[] content;
					try (InputStream stream = in.getInputStream(entry)) {
						content = stream.readAllBytes();
					}
					if (name.endsWith(CLASS_SUFFIX)) {
						ClassReader reader = new ClassReader(content);
						ClassWriter writer = new ClassWriter(0);
						reader.accept(new LambdaAwareClassRemapper(writer, remapper), 0);
						content = writer.toByteArray();
						name = remapper.map(reader.getClassName()) + CLASS_SUFFIX;
						classCount++;
					}
					if (written.add(name)) {
						out.putNextEntry(new ZipEntry(name));
						out.write(content);
						out.closeEntry();
					}
				}
				AmidstLogger.info("Wrote {} classes with readable names to {}", classCount, readableJar);
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(part);
			throw e;
		}
		Files.move(part, readableJar, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * The superclass and interfaces of every class, so members can be found
	 * in the classes that declare them.
	 */
	private static Map<String, MinecraftRemapper.ClassHeader> readHeaders(ZipFile jar) throws IOException {
		Map<String, MinecraftRemapper.ClassHeader> result = new HashMap<>();
		Enumeration<? extends ZipEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.getName().endsWith(CLASS_SUFFIX)) {
				try (InputStream stream = jar.getInputStream(entry)) {
					ClassReader reader = new ClassReader(stream);
					result.put(reader.getClassName(), new MinecraftRemapper.ClassHeader(reader.getSuperName(), reader.getInterfaces()));
				}
			}
		}
		return result;
	}

	private static void writeManifest(ZipOutputStream out) throws IOException {
		out.putNextEntry(new ZipEntry(MANIFEST));
		out.write("Manifest-Version: 1.0\r\nCreated-By: Amidst\r\n\r\n".getBytes(StandardCharsets.UTF_8));
		out.closeEntry();
	}

	/**
	 * The signature of the jar does not fit its new contents, and the
	 * manifest is replaced with one without digests.
	 */
	private static boolean isSignature(String name) {
		if (!name.startsWith("META-INF/")) {
			return false;
		}
		String upper = name.toUpperCase();
		return upper.equals(MANIFEST) || upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
	}

	/**
	 * A lambda calls the method of its interface by name. ASM only hands the
	 * remapper that name without the interface, so the interface and the
	 * descriptor of the method are taken from the arguments of the bootstrap
	 * method instead.
	 */
	private static class LambdaAwareClassRemapper extends ClassRemapper {
		private final MinecraftRemapper minecraftRemapper;

		private LambdaAwareClassRemapper(ClassVisitor classVisitor, MinecraftRemapper remapper) {
			super(classVisitor, remapper);
			this.minecraftRemapper = remapper;
		}

		@Override
		protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
			return new MethodRemapper(api, methodVisitor, remapper) {
				@Override
				public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
					String mappedName = name;
					if (LAMBDA_METAFACTORY.equals(bootstrapMethodHandle.getOwner())
							&& bootstrapMethodArguments.length > 0
							&& bootstrapMethodArguments[0] instanceof Type) {
						Type functionalInterface = Type.getReturnType(descriptor);
						if (functionalInterface.getSort() == Type.OBJECT) {
							String samDescriptor = ((Type) bootstrapMethodArguments[0]).getDescriptor();
							mappedName = minecraftRemapper.mapMethodName(functionalInterface.getInternalName(), name, samDescriptor);
						}
					}
					super.visitInvokeDynamicInsn(mappedName, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
				}
			};
		}
	}
}
