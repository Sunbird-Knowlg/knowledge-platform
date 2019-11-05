package org.sunbird.cache.impl.handler;

import org.sunbird.cache.handler.ICacheHandler;

import java.util.Arrays;

public class CategoryCacheHandler  implements ICacheHandler {
    @Override
    public Object execute(String operation, String cacheKey, String objectKey) {
        //TODO: Get the Framework Hierarchy from Cassandra and load it to cache
        //TODO: Filter out required category and return the data.
        System.out.println("CategoryCacheHandler :: execute");
        return Arrays.asList("Science","English");
    }
}
