package org.sunbird.cache.mgr;

import org.sunbird.cache.common.CacheErrorCode;
import org.sunbird.cache.handler.ICacheHandler;
import org.sunbird.cache.util.RedisCacheUtil;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class LocalCacheManager implements ICacheManager {

    ICacheHandler handler;

    protected void init(String... channels){
        subscribe(channels);
    }

    @Override
    public void publish(String channel, String message) {
        RedisCacheUtil.publish(channel, message);
    }

    @Override
    public void subscribe(String... channels) {
        JedisPubSub pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                processSubscription(channel, message);
            }
        };

        ExecutorService pool = null;
        try {
            pool = Executors.newFixedThreadPool(1);
            pool.execute(new Runnable() {
                public void run() {
                    RedisCacheUtil.subscribe(pubSub, channels);
                }
            });
        } catch (Exception e) {
            TelemetryManager.error("Exception Occured While Subscribing to channels : " + channels + " | Exception is : " + e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_SUBSCRIBE_CHANNEL_ERROR.name(),e.getMessage());
        } finally {
            if (null != pool)
                pool.shutdown();
        }
    }

    protected abstract void processSubscription(String channel, String message);


    @Override
    public String getString(String key) {
        return null;
    }

    @Override
    public void setString(String key, String data, int ttl) {

    }

    @Override
    public List<String> getList(String key) {
        return null;
    }

    @Override
    public void setList(String key, List<String> list, int ttl) {

    }

    @Override
    public void increment(String key) {

    }

}
