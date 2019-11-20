package org.sunbird.cache.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.sunbird.cache.impl.handler.CategoryCacheHandler;
import org.sunbird.cache.impl.handler.LicenseCacheHandler;
import org.sunbird.cache.mgr.RedisCacheManager;

import java.util.ArrayList;
import java.util.List;

public class LicenseCache extends RedisCacheManager {
    private static String object = "license";

    public LicenseCache(){
        handler = new LicenseCacheHandler();
    }

    @Override
    public String getKey(String... params) {
        return null;
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
        List<String> valueList = getListData(key, object);
        if(CollectionUtils.isNotEmpty(valueList))
            return valueList;
        else
            return new ArrayList<>();
    }

    @Override
    public void setList(String key, List<String> list, int ttl) {
        List<Object> objectList = new ArrayList<>(list);
        setListData(key, "license", objectList);
    }

    @Override
    public void increment(String key) {

    }

    @Override
    public void delete(String... key) {

    }

}
