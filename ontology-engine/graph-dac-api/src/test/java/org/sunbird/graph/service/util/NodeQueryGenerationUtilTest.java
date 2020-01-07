package org.sunbird.graph.service.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeQueryGenerationUtilTest {

	@Test
	public void testGenerateUpdateNodesQuery() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> metadata = new HashMap<String, Object>() {{
			put("version", "3");
			put("status", "Review");
		}};
		Map<String, Object> param = new HashMap<String, Object>();
		String query = NodeQueryGenerationUtil.generateUpdateNodesQuery("domain", ids, metadata, param);
		Assert.assertTrue(StringUtils.isNoneBlank(query));
		Assert.assertTrue(MapUtils.isNotEmpty(param));
		Assert.assertTrue(param.size() == 3);
		Assert.assertEquals("MATCH(n:domain) WHERE n.IL_UNIQUE_ID IN {identifiers} SET n.version = {1} ,  n.status = {2}  RETURN n AS ee ;", query);
	}

}
