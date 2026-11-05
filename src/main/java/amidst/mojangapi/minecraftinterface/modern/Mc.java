package amidst.mojangapi.minecraftinterface.modern;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import amidst.documentation.ThreadSafe;

/**
 * Calls into Minecraft by the names of its classes and members, which are
 * readable since Minecraft 26.1, and in a copy of an older jar that Amidst
 * made with Mojang's obfuscation map. Methods are found by their name and
 * by the runtime types of the arguments, so callers do not need to spell
 * out the parameter types of every call.
 */
@ThreadSafe
public class Mc {
	private final ClassLoader classLoader;
	private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
	private final Map<Signature, Executable> executables = new ConcurrentHashMap<>();

	public Mc(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public Class<?> type(String className) {
		return classes.computeIfAbsent(className, name -> {
			try {
				return Class.forName(name, true, classLoader);
			} catch (ClassNotFoundException e) {
				throw new McException("Minecraft has no class " + name, e);
			}
		});
	}

	/**
	 * Whether this Minecraft has a class of the given name, so callers can
	 * follow the renames between versions.
	 */
	public boolean hasType(String className) {
		try {
			type(className);
			return true;
		} catch (McException e) {
			return false;
		}
	}

	/**
	 * Whether the given class has a public method of the given name, whatever
	 * its parameters.
	 */
	public boolean hasMethod(String className, String methodName) {
		for (Method method : type(className).getMethods()) {
			if (method.getName().equals(methodName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the given class has a public method of the given name with the
	 * given number of parameters.
	 */
	public boolean hasMethod(String className, String methodName, int parameterCount) {
		for (Method method : type(className).getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the given object has a public method of the given name, so
	 * callers can follow the renames between versions.
	 */
	public boolean canCall(Object target, String methodName) {
		for (Method method : target.getClass().getMethods()) {
			if (method.getName().equals(methodName)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasConstructor(String className, int parameterCount) {
		for (Constructor<?> constructor : type(className).getConstructors()) {
			if (constructor.getParameterCount() == parameterCount) {
				return true;
			}
		}
		return false;
	}

	public boolean hasField(String className, String fieldName) {
		try {
			type(className).getField(fieldName);
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}

	public Object get(String className, String fieldName) {
		try {
			return type(className).getField(fieldName).get(null);
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to read " + className + "." + fieldName, e);
		}
	}

	/**
	 * A public field of an object, such as the coordinates of a chunk
	 * position before they became record components.
	 */
	public Object get(Object target, String fieldName) {
		try {
			return target.getClass().getField(fieldName).get(target);
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to read " + fieldName + " of " + target.getClass().getName(), e);
		}
	}

	/**
	 * A field that Minecraft does not expose, for the few places where an
	 * old version keeps what Amidst needs to itself.
	 */
	public Object getPrivate(Object target, String fieldName) {
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.get(target);
			} catch (NoSuchFieldException e) {
				// keep looking in the superclass
			} catch (ReflectiveOperationException | RuntimeException e) {
				throw new McException("unable to read " + fieldName + " of " + target.getClass().getName(), e);
			}
		}
		throw new McException(target.getClass().getName() + " has no field " + fieldName);
	}

	/**
	 * Calls a static method that Minecraft does not expose.
	 */
	public Object callPrivateStatic(String className, String methodName, Object... args) {
		for (Method method : type(className).getDeclaredMethods()) {
			if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())
					&& accepts(method.getParameterTypes(), argTypes(args))) {
				try {
					method.setAccessible(true);
					return method.invoke(null, args);
				} catch (InvocationTargetException e) {
					throw new McException("Minecraft failed in " + method, e.getCause());
				} catch (ReflectiveOperationException | RuntimeException e) {
					throw new McException("unable to call " + method, e);
				}
			}
		}
		throw new McException("Minecraft has no static method " + methodName + " in " + className);
	}

	/**
	 * An object that implements one of Minecraft's interfaces, for the
	 * callbacks Minecraft asks for.
	 */
	public Object proxy(String interfaceName, InvocationHandler handler) {
		Class<?> type = type(interfaceName);
		return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	public Object call(Object target, String methodName, Object... args) {
		Objects.requireNonNull(target, methodName);
		return invoke(find(target.getClass(), methodName, false, args), target, args);
	}

	public Object callStatic(String className, String methodName, Object... args) {
		return invoke(find(type(className), methodName, true, args), null, args);
	}

	public Object create(String className, Object... args) {
		Executable constructor = find(type(className), null, false, args);
		try {
			return ((Constructor<?>) constructor).newInstance(args);
		} catch (InvocationTargetException e) {
			throw new McException("Minecraft failed in new " + className, e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to call new " + className, e);
		}
	}

	/**
	 * A handle for a method that is called too often for the lookup by
	 * argument types.
	 */
	public MethodHandle handle(String className, String methodName, Class<?>... parameterTypes) {
		try {
			Method method = publicMethod(type(className).getMethod(methodName, parameterTypes));
			return MethodHandles.publicLookup().unreflect(method);
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to find " + className + "." + methodName, e);
		}
	}

	/**
	 * A handle for a constructor that is called too often for the lookup by
	 * argument types.
	 */
	public MethodHandle constructorHandle(String className, Class<?>... parameterTypes) {
		try {
			return MethodHandles.publicLookup().unreflectConstructor(type(className).getConstructor(parameterTypes));
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to find the constructor of " + className, e);
		}
	}

	private static Object invoke(Executable executable, Object target, Object[] args) {
		try {
			return ((Method) executable).invoke(target, args);
		} catch (InvocationTargetException e) {
			throw new McException("Minecraft failed in " + executable, e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new McException("unable to call " + executable, e);
		}
	}

	/**
	 * @param methodName the name of the method, or null for a constructor
	 */
	private Executable find(Class<?> owner, String methodName, boolean isStatic, Object[] args) {
		Class<?>[] argTypes = argTypes(args);
		return executables.computeIfAbsent(
				new Signature(owner, methodName, argTypes),
				signature -> lookup(owner, methodName, isStatic, argTypes));
	}

	private static Class<?>[] argTypes(Object[] args) {
		Class<?>[] argTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			argTypes[i] = args[i] == null ? null : args[i].getClass();
		}
		return argTypes;
	}

	private static Executable lookup(Class<?> owner, String methodName, boolean isStatic, Class<?>[] argTypes) {
		Executable[] candidates = methodName == null ? owner.getConstructors() : owner.getMethods();
		for (Executable candidate : candidates) {
			if (methodName != null && !candidate.getName().equals(methodName)) {
				continue;
			}
			if (methodName != null && Modifier.isStatic(candidate.getModifiers()) != isStatic) {
				continue;
			}
			if (accepts(candidate.getParameterTypes(), argTypes)) {
				return candidate instanceof Method ? publicMethod((Method) candidate) : candidate;
			}
		}
		throw new McException("Minecraft has no " + (methodName == null ? "constructor" : "method " + methodName)
				+ " in " + owner.getName() + " for " + Arrays.toString(argTypes));
	}

	private static boolean accepts(Class<?>[] parameterTypes, Class<?>[] argTypes) {
		if (parameterTypes.length != argTypes.length) {
			return false;
		}
		for (int i = 0; i < parameterTypes.length; i++) {
			Class<?> parameterType = box(parameterTypes[i]);
			if (argTypes[i] == null ? parameterTypes[i].isPrimitive() : !parameterType.isAssignableFrom(argTypes[i])) {
				return false;
			}
		}
		return true;
	}

	private static Class<?> box(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		List<Class<?>> primitives = Arrays.asList(int.class, long.class, boolean.class, double.class, float.class, short.class, byte.class, char.class);
		List<Class<?>> boxes = Arrays.asList(Integer.class, Long.class, Boolean.class, Double.class, Float.class, Short.class, Byte.class, Character.class);
		return boxes.get(primitives.indexOf(type));
	}

	/**
	 * Methods of objects are often declared by classes that are not public,
	 * such as lambdas or private implementations of public interfaces. Those
	 * can only be called through the public type that declares them.
	 */
	private static Method publicMethod(Method method) {
		if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
			return method;
		}
		Deque<Class<?>> queue = new ArrayDeque<>();
		Set<Class<?>> seen = new HashSet<>();
		queue.add(method.getDeclaringClass());
		while (!queue.isEmpty()) {
			Class<?> type = queue.poll();
			if (type == null || !seen.add(type)) {
				continue;
			}
			if (Modifier.isPublic(type.getModifiers())) {
				try {
					return type.getMethod(method.getName(), method.getParameterTypes());
				} catch (NoSuchMethodException e) {
					// keep looking in the supertypes
				}
			}
			queue.add(type.getSuperclass());
			queue.addAll(Arrays.asList(type.getInterfaces()));
		}
		throw new McException("no public type declares " + method);
	}

	private static class Signature {
		private final Class<?> owner;
		private final String name;
		private final Class<?>[] argTypes;

		private Signature(Class<?> owner, String name, Class<?>[] argTypes) {
			this.owner = owner;
			this.name = name;
			this.argTypes = argTypes;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Signature)) {
				return false;
			}
			Signature that = (Signature) other;
			return owner == that.owner && Objects.equals(name, that.name) && Arrays.equals(argTypes, that.argTypes);
		}

		@Override
		public int hashCode() {
			return Objects.hash(owner, name, Arrays.hashCode(argTypes));
		}
	}

	/**
	 * Thrown when Minecraft does not have the classes and members Amidst
	 * expects, or when Minecraft itself fails.
	 */
	public static class McException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public McException(String message, Throwable cause) {
			super(message, cause);
		}

		public McException(String message) {
			super(message);
		}
	}
}
