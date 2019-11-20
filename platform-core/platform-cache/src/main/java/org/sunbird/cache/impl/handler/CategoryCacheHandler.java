package org.sunbird.cache.impl.handler;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cache.common.CacheHandlerOperation;
import org.sunbird.cache.handler.ICacheHandler;
import org.sunbird.cache.util.RedisCacheUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CategoryCacheHandler implements ICacheHandler {
	@Override
	public Object execute(String operation, String cacheKey, String objectKey) {
        if (StringUtils.equals(CacheHandlerOperation.READ_LIST.name(), operation)) {
            List<String> result = new ArrayList<String>();
            Map<String, Object> objectHierarchy = getHierarchy(objectKey);
            refreshCache(objectKey, cacheKey, objectHierarchy, result);
            return result;
        } else {
            return null;
        }
	}

	private Map<String, Object> getHierarchy(String objectKey) {
		//TODO: Get the framework hierarchy from Hierarchy Store
		return new HashMap<String, Object>();
	}

	private void refreshCache(String objectKey, String cacheKey, Map<String, Object> hierarchy, List<String> result) {
		try {
			if (MapUtils.isNotEmpty(hierarchy)) {
				List<Map<String, Object>> categories = (List<Map<String, Object>>) hierarchy.get("categories");
				if (CollectionUtils.isNotEmpty(categories)) {
					for (Map<String, Object> category : categories) {
						String catName = (String) category.get("code");
						List<String> terms = getTerms(category, "terms");
						if (CollectionUtils.isNotEmpty(terms)) {
							String key = getKey(objectKey, catName);
							if (StringUtils.equalsIgnoreCase(key, cacheKey))
								result.addAll(terms);
							RedisCacheUtil.saveList(key, terms.stream().map(obj -> (Object) obj).collect(Collectors.toList()));
						}
					}
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}

	private static String getKey(String framework, String category) {
		return StringUtils.isNotBlank(framework) && StringUtils.isNotBlank(category)? ("cat_" + framework + category):"";
	}

	private static List<String> getTerms(Map<String, Object> category, String key) {
		List<String> returnTerms = new ArrayList<String>();
		if (null != category && !category.isEmpty()) {
			List<Map<String, Object>> terms = (List<Map<String, Object>>) category.get(key);
			if (null != terms)
				for (Map<String, Object> term : terms) {
					Object termName = term.get("name");
					if (StringUtils.isNotBlank((String) termName)) {
						returnTerms.add((String) termName);
						List<String> childTerms = getTerms(term, "children");
						if (CollectionUtils.isNotEmpty(childTerms))
							returnTerms.addAll(childTerms);
					}
				}
		}
		return returnTerms;
	}
}
