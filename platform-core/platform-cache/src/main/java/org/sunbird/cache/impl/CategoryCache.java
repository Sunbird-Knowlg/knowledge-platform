package org.sunbird.cache.impl;

import org.sunbird.cache.impl.handler.CategoryCacheHandler;
import org.sunbird.cache.mgr.RedisCacheManager;

import java.util.List;

public class CategoryCache extends RedisCacheManager {

    public CategoryCache(){
        handler = new CategoryCacheHandler();
    }

    @Override
    public String getKey(String... params) {
        return "cat_" + params[0].toLowerCase() + "_" + params[1].toLowerCase();
    }

    @Override
    public String getString(String key) {
        return null;
    }

    @Override
    public void setString(String key, String data, int ttl) {

    }

    @Override
    public List<String> getList(String key) {
        return getListData(key, getObjectKey(key));
    }

    @Override
    public void setList(String key, List<String> list, int ttl) {

    }

    @Override
    public void increment(String key) {

    }

    @Override
    public void delete(String... key) {

    }

    private String getObjectKey(String cacheKey){
        return cacheKey.split("_")[1];
    }
}
