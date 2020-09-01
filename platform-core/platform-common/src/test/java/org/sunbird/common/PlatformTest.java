package org.sunbird.common;

import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlatformTest {

	@BeforeClass
	public static void init() {
		Platform.config = ConfigFactory.parseMap(new HashMap<String, Object>() {{
			put("test.str", "strval");
			put("test.int", 100);
			put("test.bool", true);
			put("test.long", 1380914990);
			put("test.double", 900923.0);
			put("content.graph_ids", Arrays.asList("es","ko"));
			put("test.strlist", new ArrayList<String>() {{
				add("val1");
				add("val2");
			}});
			put("test.map", new HashMap<String, Object>() {{
				put("key1", "val1");
				put("key2","val2");
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

	@Test
	public void testGetLongWithValidConfig() {
		Long result = Platform.getLong("test.long", 0L);
		Long expected = 1380914990L;
		Assert.assertTrue(null != result );
		Assert.assertEquals(expected, result);
	}

	@Test
	public void testGetLongWithInvalidConfig() {
		Long result = Platform.getLong("test.long.1", 0L);
		Long expected = 0L;
		Assert.assertTrue(null != result );
		Assert.assertEquals(expected, result);
	}

	@Test
	public void testGetDoubleWithValidConfig() {
		Double result = Platform.getDouble("test.long", 0.0);
		Double expected = 1.38091499E9;
		Assert.assertTrue(null != result );
		Assert.assertEquals(expected, result);
	}

	@Test
	public void testGetDoubleWithInvalidConfig() {
		Double result = Platform.getDouble("test.long.1", 0.0);
		Double expected = 0.0;
		Assert.assertTrue(null != result );
		Assert.assertEquals(expected, result);
	}

    @Test
    public void testGetGraphIds() {
        List<String> values = Platform.getGraphIds("content");
        Assert.assertNotNull(values);
        Assert.assertEquals(2, values.size());
    }

    @Test
    public void testGetGraphIdsInvalidService() {
        List<String> values = Platform.getGraphIds("search");
        Assert.assertNotNull(values);
        Assert.assertEquals(0, values.size());
    }

    @Test
    public void testGetTimeout() {
        int timeout = Platform.getTimeout();
        Assert.assertNotNull(timeout);
        Assert.assertEquals(30, timeout);
    }

	@Test
	public void testGetAnyRefWithValidConfig() {
		Map<String, Object> result = (Map<String, Object>) Platform.getAnyRef("test.map", new HashMap<String, Object>());
		Assert.assertTrue(null != result );
		Assert.assertEquals("val1", result.get("key1"));
	}

	@Test
	public void testGetAnyRefWithInvalidConfig() {
		Map<String, Object> result = (Map<String, Object>) Platform.getAnyRef("test.map1", new HashMap<String, Object>());
		Assert.assertTrue(null != result );
		Assert.assertEquals(null, result.get("key1"));
	}
}
