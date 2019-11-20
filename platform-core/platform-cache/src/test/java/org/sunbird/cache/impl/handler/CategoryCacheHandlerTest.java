package org.sunbird.cache.impl.handler;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.sunbird.cache.common.CacheHandlerOperation;
import org.sunbird.cache.handler.ICacheHandler;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class CategoryCacheHandlerTest {

	private static ICacheHandler handler = new CategoryCacheHandler();

	@Test
	public void testExecute() {
		List<String> data = (List<String>) handler.execute(CacheHandlerOperation.READ_LIST.name(), "cat_ncfsubject", "ncf");
		assertTrue(CollectionUtils.isNotEmpty(data));
	}

}
