package amidst.mojangapi.minecraftinterface.modern;

import org.junit.Assert;
import org.junit.Test;

public class McTest {
	private final Mc mc = new Mc(getClass().getClassLoader());

	@Test
	public void shouldTellWhetherAClassExists() {
		Assert.assertTrue(mc.hasType("java.util.ArrayList"));
		Assert.assertFalse(mc.hasType("java.util.NoSuchList"));
	}

	@Test
	public void shouldTellWhetherAMethodExists() {
		Assert.assertTrue(mc.hasMethod("java.util.ArrayList", "trimToSize"));
		Assert.assertFalse(mc.hasMethod("java.util.ArrayList", "trimToNothing"));
	}

	@Test(expected = Mc.McException.class)
	public void shouldFailForMethodsOfMissingClasses() {
		mc.hasMethod("java.util.NoSuchList", "trimToSize");
	}
}
