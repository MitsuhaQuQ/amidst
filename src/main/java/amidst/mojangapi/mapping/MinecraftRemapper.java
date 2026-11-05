package amidst.mojangapi.mapping;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.commons.Remapper;

import amidst.documentation.ThreadSafe;

/**
 * Turns the obfuscated names of a Minecraft jar into the readable ones of
 * Mojang's obfuscation map. The map only lists a member with the class that
 * declares it, while the jar refers to members through subclasses as well, so
 * this remapper follows the class hierarchy of the jar to find the
 * declaration.
 */
@ThreadSafe
public class MinecraftRemapper extends Remapper {
	/**
	 * The superclass and interfaces of a class of the jar, by their
	 * obfuscated internal names.
	 */
	public static class ClassHeader {
		private final String superName;
		private final String[] interfaces;

		public ClassHeader(String superName, String[] interfaces) {
			this.superName = superName;
			this.interfaces = interfaces == null ? new String[0] : interfaces;
		}
	}

	private final ProguardMappings mappings;
	private final Map<String, ClassHeader> headers;
	private final Map<String, String> methodCache = new ConcurrentHashMap<>();
	private final Map<String, String> fieldCache = new ConcurrentHashMap<>();

	public MinecraftRemapper(ProguardMappings mappings, Map<String, ClassHeader> headers) {
		this.mappings = mappings;
		this.headers = Collections.unmodifiableMap(headers);
	}

	@Override
	public String map(String internalName) {
		return mappings.mapClass(internalName);
	}

	@Override
	public String mapFieldName(String owner, String name, String descriptor) {
		return fieldCache.computeIfAbsent(owner + "." + name + ":" + descriptor, key -> {
			String result = findInHierarchy(owner, type -> mappings.mapField(type, name, descriptor));
			return result != null ? result : name;
		});
	}

	@Override
	public String mapRecordComponentName(String owner, String name, String descriptor) {
		return mapFieldName(owner, name, descriptor);
	}

	@Override
	public String mapMethodName(String owner, String name, String descriptor) {
		if (name.charAt(0) == '<' || owner.charAt(0) == '[') {
			// constructors, static initialisers and the methods of arrays
			return name;
		}
		return methodCache.computeIfAbsent(owner + "." + name + descriptor, key -> {
			String result = findInHierarchy(owner, type -> mappings.mapMethod(type, name, descriptor));
			return result != null ? result : name;
		});
	}

	/**
	 * Looks for a member in the given class, its superclasses and its
	 * interfaces. Classes outside the jar, such as those of Java and of
	 * libraries, end the search, since the map does not rename their members.
	 */
	private String findInHierarchy(String owner, java.util.function.Function<String, String> lookup) {
		Deque<String> queue = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String type = queue.poll();
			if (type == null || !seen.add(type)) {
				continue;
			}
			String result = lookup.apply(type);
			if (result != null) {
				return result;
			}
			ClassHeader header = headers.get(type);
			if (header != null) {
				queue.add(header.superName);
				Collections.addAll(queue, header.interfaces);
			}
		}
		return null;
	}
}
