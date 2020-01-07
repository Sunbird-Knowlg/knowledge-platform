package org.sunbird.graph.service.operation;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NodeAsyncOperationsTest {


	@Test
	public void testSetPrimitiveData() throws Exception {
		Method method = NodeAsyncOperations.class.getDeclaredMethod("setPrimitiveData", Map.class);
		method.setAccessible(true);
		Map<String, Object> metadata = new HashMap<String, Object>() {{
			put("testMap", new HashMap<String, Object>() {{
				put("name", "test");
				put("identifier", "123");
			}});
			put("list", new ArrayList<Map<String, Object>>() {{
				add(new HashMap<String, Object>(){{
					put("identifier","123");
				}});
				add(new HashMap<String, Object>(){{
					put("identifier","234");
				}});
			}});
		}};
		Map<String, Object> result = (Map<String, Object>) method.invoke(NodeAsyncOperations.class, metadata);
		Assert.assertNotNull(result);
		Assert.assertEquals("{\"identifier\":\"123\",\"name\":\"test\"}", result.get("testMap"));
	}

}
