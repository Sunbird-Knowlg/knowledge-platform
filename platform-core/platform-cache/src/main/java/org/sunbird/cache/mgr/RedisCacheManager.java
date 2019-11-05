package org.sunbird.cache.mgr;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cache.common.CacheHandlerOperation;
import org.sunbird.cache.handler.ICacheHandler;
import org.sunbird.cache.util.RedisCacheUtil;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.List;


/**
 * Base Class for Redis Based Cache Implementation
 *
 * @author Kumar Gauraw
 */
public abstract class RedisCacheManager implements ICacheManager {

    protected ICacheHandler handler;

    //this method can be called directly from final implementation class getString(String key)
    protected String getStringData(String cacheKey, String objectKey) {
        try {
            String data = RedisCacheUtil.getString(cacheKey);
            if (StringUtils.isBlank(data) && null != handler) {
                data = (String) handler.execute(CacheHandlerOperation.READ_STRING.name(), cacheKey, objectKey);
            }
            return data;
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While  Fetching Data For Key : " + cacheKey + " | Exception is : ", e);
            if (e instanceof ResourceNotFoundException)
                throw e;
        }
        return null;
    }

    protected List<String> getListData(String cacheKey, String objectKey){
        try{
            List<String> data = RedisCacheUtil.getList(cacheKey);
            if(CollectionUtils.isEmpty(data) && null!=handler){
                data = (List<String>)handler.execute(CacheHandlerOperation.READ_LIST.name(),cacheKey,objectKey);
            }
            return data;
        }catch(Exception e){
            TelemetryManager.error("Exception Occurred While  Fetching Data For Key : " + cacheKey + " | Exception is : ", e);
            if (e instanceof ResourceNotFoundException)
                throw e;
        }
        return null;
    }

    @Override
    public Object getObject(String key) {
        return null;
    }

    @Override
    public void setObject(String key, Object data) {

    }

    @Override
    public void publish(String channel, String message) {

    }

    @Override
    public void subscribe(String... channels) {

    }
}
