package org.sunbird.common;

import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PlatformTest {

	@BeforeClass
	public static void init() {
		Platform.config = ConfigFactory.parseMap(new HashMap<String, Object>() {{
			put("test.str", "strval");
			put("test.int", 100);
			put("test.bool", true);
			put("test.strlist", new ArrayList<String>() {{
				add("val1");
				add("val2");
			}});
		}}).resolve();
	}

	@Test
	public void testGetStringWithValidConfig() {
		String str = Platform.getString("test.str", "def_str_val");
		Assert.assertEquals("strval", str);
	}

	@Test
	public void testGetStringWithInvalidConfig() {
		String str = Platform.getString("test.str.1", "def_str_val");
		Assert.assertEquals("def_str_val", str);
	}

	@Test
	public void testGetIntegerWithValidConfig() {
		int result = Platform.getInteger("test.int", 0);
		Assert.assertEquals(100, result);
	}

	@Test
	public void testGetIntegerWithInvalidConfig() {
		int result = Platform.getInteger("test.int.1", 0);
		Assert.assertEquals(0, result);
	}

	@Test
	public void testGetBooleanWithValidConfig() {
		boolean result = Platform.getBoolean("test.bool", false);
		Assert.assertEquals(true, result);
	}

	@Test
	public void testGetBooleanWithInvalidConfig() {
		boolean result = Platform.getBoolean("test.bool.1", false);
		Assert.assertEquals(false, result);
	}

	@Test
	public void testGetStringListWithValidConfig() {
		List<String> result = Platform.getStringList("test.strlist", new ArrayList<String>());
		Assert.assertTrue(null != result && !result.isEmpty());
		Assert.assertTrue(result.size() == 2);
		Assert.assertTrue(result.contains("val1"));
	}

	@Test
	public void testGetStringListWithInvalidConfig() {
		List<String> result = Platform.getStringList("test.strlist.1", new ArrayList<String>());
		Assert.assertTrue(null != result && result.isEmpty());
		Assert.assertTrue(result.size() == 0);
	}
}
