package amidst.mojangapi.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import amidst.documentation.Immutable;

/**
 * The obfuscation map that Mojang publishes for every Minecraft version since
 * 1.14.4, in the format of ProGuard. The file lists the readable names and
 * the obfuscated names they were turned into; this class answers the reverse
 * question, from an obfuscated name back to the readable one, with the
 * descriptors of the obfuscated jar as keys.
 */
@Immutable
public class ProguardMappings {
	private static final String MEMBER_INDENT = "    ";
	private static final String ARROW = " -> ";

	public static ProguardMappings read(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return parse(reader);
		}
	}

	public static ProguardMappings parse(BufferedReader reader) throws IOException {
		List<String> lines = new ArrayList<>();
		String line;
		while ((line = reader.readLine()) != null) {
			if (!line.isEmpty() && line.charAt(0) != '#') {
				lines.add(line);
			}
		}
		return parse(lines);
	}

	private static ProguardMappings parse(List<String> lines) {
		// The members of a class are written with readable type names, so all
		// classes have to be known before the descriptors can be built.
		Map<String, String> readableToObfuscated = new HashMap<>();
		for (String line : lines) {
			if (isClassLine(line)) {
				int arrow = line.indexOf(ARROW);
				String readable = toInternalName(line.substring(0, arrow));
				String obfuscated = toInternalName(line.substring(arrow + ARROW.length(), line.length() - 1));
				readableToObfuscated.put(readable, obfuscated);
			}
		}
		Map<String, ClassMapping> classes = new HashMap<>();
		ClassMapping current = null;
		for (String line : lines) {
			if (isClassLine(line)) {
				int arrow = line.indexOf(ARROW);
				String readable = toInternalName(line.substring(0, arrow));
				String obfuscated = toInternalName(line.substring(arrow + ARROW.length(), line.length() - 1));
				current = new ClassMapping(obfuscated, readable);
				classes.put(obfuscated, current);
			} else if (current != null) {
				parseMember(line.trim(), current, readableToObfuscated);
			}
		}
		return new ProguardMappings(classes);
	}

	private static boolean isClassLine(String line) {
		return !line.startsWith(MEMBER_INDENT) && !Character.isWhitespace(line.charAt(0)) && line.endsWith(":");
	}

	/**
	 * A field is written as {@code type name -> obfuscated}, a method as
	 * {@code [from:to:]type name(argument,types)[:from[:to]] -> obfuscated}.
	 */
	private static void parseMember(String line, ClassMapping owner, Map<String, String> readableToObfuscated) {
		int arrow = line.lastIndexOf(ARROW);
		if (arrow < 0) {
			return;
		}
		String obfuscatedName = line.substring(arrow + ARROW.length());
		String left = stripLineNumbers(line.substring(0, arrow));
		int paren = left.indexOf('(');
		if (paren >= 0) {
			int close = left.indexOf(')', paren);
			if (close < 0) {
				return;
			}
			int space = left.lastIndexOf(' ', paren);
			String returnType = left.substring(0, space);
			String name = left.substring(space + 1, paren);
			String arguments = left.substring(paren + 1, close);
			StringBuilder descriptor = new StringBuilder("(");
			if (!arguments.isEmpty()) {
				for (String argument : arguments.split(",")) {
					descriptor.append(toDescriptor(argument, readableToObfuscated));
				}
			}
			descriptor.append(')').append(toDescriptor(returnType, readableToObfuscated));
			owner.methods.put(obfuscatedName + descriptor, name);
		} else {
			int space = left.lastIndexOf(' ');
			if (space < 0) {
				return;
			}
			String type = left.substring(0, space);
			String name = left.substring(space + 1);
			owner.fields.put(obfuscatedName + ":" + toDescriptor(type, readableToObfuscated), name);
			owner.fieldsByName.putIfAbsent(obfuscatedName, name);
		}
	}

	private static String stripLineNumbers(String member) {
		String result = member;
		while (true) {
			int colon = result.indexOf(':');
			if (colon <= 0 || !isNumber(result.substring(0, colon))) {
				return result;
			}
			result = result.substring(colon + 1);
		}
	}

	private static boolean isNumber(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}
		return !text.isEmpty();
	}

	/**
	 * The descriptor of a type as it is written in the obfuscated jar.
	 */
	static String toDescriptor(String readableType, Map<String, String> readableToObfuscated) {
		String type = readableType.trim();
		StringBuilder result = new StringBuilder();
		while (type.endsWith("[]")) {
			result.append('[');
			type = type.substring(0, type.length() - 2);
		}
		switch (type) {
		case "void":
			return result.append('V').toString();
		case "boolean":
			return result.append('Z').toString();
		case "byte":
			return result.append('B').toString();
		case "char":
			return result.append('C').toString();
		case "short":
			return result.append('S').toString();
		case "int":
			return result.append('I').toString();
		case "long":
			return result.append('J').toString();
		case "float":
			return result.append('F').toString();
		case "double":
			return result.append('D').toString();
		default:
			String internalName = toInternalName(type);
			return result.append('L').append(readableToObfuscated.getOrDefault(internalName, internalName)).append(';').toString();
		}
	}

	private static String toInternalName(String className) {
		return className.trim().replace('.', '/');
	}

	private final Map<String, ClassMapping> classes;

	private ProguardMappings(Map<String, ClassMapping> classes) {
		this.classes = Collections.unmodifiableMap(classes);
	}

	public int getClassCount() {
		return classes.size();
	}

	/**
	 * The readable internal name of an obfuscated class, or the given name for
	 * classes that are not obfuscated, such as those of libraries.
	 */
	public String mapClass(String obfuscatedInternalName) {
		ClassMapping mapping = classes.get(obfuscatedInternalName);
		return mapping == null ? obfuscatedInternalName : mapping.readableName;
	}

	public boolean hasClass(String obfuscatedInternalName) {
		return classes.containsKey(obfuscatedInternalName);
	}

	/**
	 * The readable name of a field declared by the given obfuscated class, or
	 * null if the class does not declare it.
	 */
	public String mapField(String obfuscatedOwner, String obfuscatedName, String descriptor) {
		ClassMapping mapping = classes.get(obfuscatedOwner);
		if (mapping == null) {
			return null;
		}
		String result = mapping.fields.get(obfuscatedName + ":" + descriptor);
		return result != null ? result : mapping.fieldsByName.get(obfuscatedName);
	}

	/**
	 * The readable name of a method declared by the given obfuscated class, or
	 * null if the class does not declare it.
	 */
	public String mapMethod(String obfuscatedOwner, String obfuscatedName, String descriptor) {
		ClassMapping mapping = classes.get(obfuscatedOwner);
		return mapping == null ? null : mapping.methods.get(obfuscatedName + descriptor);
	}

	private static class ClassMapping {
		@SuppressWarnings("unused")
		private final String obfuscatedName;
		private final String readableName;
		private final Map<String, String> fields = new HashMap<>();
		private final Map<String, String> fieldsByName = new HashMap<>();
		private final Map<String, String> methods = new HashMap<>();

		private ClassMapping(String obfuscatedName, String readableName) {
			this.obfuscatedName = obfuscatedName;
			this.readableName = readableName;
		}
	}
}
