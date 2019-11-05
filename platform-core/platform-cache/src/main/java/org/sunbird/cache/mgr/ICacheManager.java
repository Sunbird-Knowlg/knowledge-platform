package org.sunbird.cache.mgr;

import java.util.List;

/**
 * Contract for Cache Management
 *
 * @author Kumar Gauraw
 */
public interface ICacheManager {

    /**
     * This method provides key generation implementation for cache.
     *
     * @param params
     * @return String
     */
    public String getKey(String... params);

    /**
     * This method provides implementation for read operation with String value
     *
     * @param key
     * @return String
     */
    public String getString(String key);

    /**
     * This method provides implementation for write/save operation with String value
     *
     * @param key
     * @param data
     * @param ttl
     */
    public void setString(String key, String data, int ttl);

    /**
     * This method provides implementation for read operation with List Value
     *
     * @param key
     * @return List<String>
     */
    public List<String> getList(String key);

    /**
     * This method provides implementation for write/save operation with List Value
     *
     * @param key
     * @param list
     * @param ttl
     */
    public void setList(String key, List<String> list, int ttl);

    /**
     * This method provides implementation for increment operation for value of given key
     *
     * @param key
     */
    public void increment(String key);

    /**
     * This method provides implementation for reset/delete operation for given key/keys
     *
     * @param key
     */
    public void delete(String... key);

    /**
     * This method provides implementation for read operation for given key
     *
     * @param key
     * @return Object
     */
    public Object getObject(String key);

    /**
     * This method provides implementation for write/save operation for given key
     *
     * @param key
     * @param data
     */
    public void setObject(String key, Object data);

    /**
     * This method provides implementation for publish message operation to Redis Channel.
     *
     * @param channel
     * @param message
     */
    public void publish(String channel, String message);

    /**
     * This method provides implementation for subscribe operation to Redis Channel.
     *
     * @param channels
     */
    public void subscribe(String... channels);

}
