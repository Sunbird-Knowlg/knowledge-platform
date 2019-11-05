package org.sunbird.cache.util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.cache.common.CacheErrorCode;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.sunbird.cache.connection.RedisConnector.getConnection;
import static org.sunbird.cache.connection.RedisConnector.returnConnection;

/**
 * This Util Class Provide All CRUD Methods for Redis Cache.
 *
 * @author Kumar Gauraw
 */
public class RedisCacheUtil {

    /**
     * This method store string data into cache for given Key
     *
     * @param key
     * @param value
     * @param ttl
     */
    public static void saveString(String key, String value, int ttl) {
        Jedis jedis = getConnection();
        try {
            jedis.del(key);
            jedis.set(key, value);
            if (ttl > 0)
                jedis.expire(key, ttl);
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Saving String Data to Redis Cache for Key : " + key + "| Exception is:", e);
            //TODO: Suppress this exception.
            throw new ServerException(CacheErrorCode.ERR_CACHE_SAVE_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    /**
     * This method read string data from cache for a given key
     *
     * @param key
     * @return String
     */
    public static String getString(String key) {
        Jedis jedis = getConnection();
        try {
            return jedis.get(key);
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Fetching String Data from Redis Cache for Key : " + key + "| Exception is:", e);
            //TODO: Suppress this exception.
            throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    // TODO: always considering object as string. need to change this.

    /**
     * This method store/save list data into cache for given Key
     *
     * @param key
     * @param values
     */
    public static void saveList(String key, List<Object> values) {
        Jedis jedis = getConnection();
        try {
            jedis.del(key);
            for (Object val : values) {
                jedis.sadd(key, (String) val);
            }
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Saving List Data to Redis Cache for Key : " + key + "| Exception is:", e);
            //TODO: Suppress this exception.
            throw new ServerException(CacheErrorCode.ERR_CACHE_SAVE_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    /**
     * This method read list data from cache for a given key
     *
     * @param key
     * @return List
     */
    public static List<String> getList(String key) {
        Jedis jedis = getConnection();
        try {
            Set<String> set = jedis.smembers(key);
            List<String> list = new ArrayList<>(set);
            return list;
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Fetching List Data from Redis Cache for Key : " + key + "| Exception is:", e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    /**
     * This method delete data from cache for given key/keys
     *
     * @param keys
     */
    public static void delete(String... keys) {
        Jedis jedis = getConnection();
        try {
            jedis.del(keys);
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Deleting Records From Redis Cache for Identifiers : " + Arrays.asList(keys) + " | Exception is : ", e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_DELETE_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    /**
     * This method delete data from cache for all key/keys matched with given pattern
     *
     * @param pattern
     */
    public static void deleteByPattern(String pattern) {
        if (StringUtils.isNotBlank(pattern) && !StringUtils.equalsIgnoreCase(pattern, "*")) {
            Jedis jedis = getConnection();
            try {
                Set<String> keys = jedis.keys(pattern);
                if (keys != null && keys.size() > 0) {
                    List<String> keyList = new ArrayList<>(keys);
                    jedis.del(keyList.toArray(new String[keyList.size()]));
                }
            } catch (Exception e) {
                TelemetryManager.error("Exception Occurred While Deleting Records From Redis Cache for Pattern : " + pattern + " | Exception is : ", e);
                throw new ServerException(CacheErrorCode.ERR_CACHE_SAVE_PROPERTY_ERROR.name(), e.getMessage());
            } finally {
                returnConnection(jedis);
            }
        }
    }

    /**
     * This method increment the value by 1 into cache for given key and returns the new value
     *
     * @param key
     * @return Double
     */
    public static Double getIncVal(String key) {
        Jedis jedis = getConnection();
        try {
            double inc = 1.0;
            double value = jedis.incrByFloat(key, inc);
            return value;
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Incrementing Value for Key : " + key + " | Exception is : ", e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.name(), e.getMessage());
        } finally {
            returnConnection(jedis);
        }
    }

    /**
     * This method push given string message to given channel, which can be consumed by all subscribers of that channel.
     *
     * @param channel
     * @param data
     */
    public static void publish(String channel, String data) {
        if (StringUtils.isNotBlank(channel) && StringUtils.isNotBlank(data)) {
            Jedis jedis = getConnection();
            try {
                jedis.publish(channel, data);
            } catch (Exception e) {
                TelemetryManager.error("Exception Occurred While Publishing Message to Redis Channel : " + channel + " for data : " + data + " | Exception is : ", e);
                throw new ServerException(CacheErrorCode.ERR_CACHE_PUBLISH_CHANNEL_ERROR.name(), e.getMessage());
            } finally {
                returnConnection(jedis);
            }
        }
    }

    /**
     * This method subscribe to given channel/channels to receive messages.
     *
     * @param pubSub
     * @param channel
     */
    public static void subscribe(JedisPubSub pubSub, String... channel) {
        if (null != channel && null != pubSub) {
            Jedis jedis = getConnection();
            try {
                jedis.subscribe(pubSub, channel);
            } catch (Exception e) {
                TelemetryManager.error("Exception Occurred While Subscribing to Redis Channel : " + channel + " | Exception is : ", e);
                throw new ServerException(CacheErrorCode.ERR_CACHE_SUBSCRIBE_CHANNEL_ERROR.name(), e.getMessage());
            }
        }
    }
}
