package org.sunbird.cache.connection;

import org.sunbird.cache.common.CacheErrorCode;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * This Class Provides Methods for Managing Connection With Redis Cache.
 *
 * @author Kumar Gauraw
 */
public class RedisConnector {

    private static JedisPool jedisPool;
    private static final String HOST = Platform.config.hasPath("redis.host") ? Platform.config.getString("redis.host") : "localhost";
    private static final int PORT = Platform.config.hasPath("redis.port") ? Platform.config.getInt("redis.port") : 6379;
    private static final int MAX_CONNECTIONS = Platform.config.hasPath("redis.maxConnections") ? Platform.config.getInt("redis.maxConnections") : 128;
    private static final int INDEX = Platform.config.hasPath("redis.dbIndex") ? Platform.config.getInt("redis.dbIndex") : 0;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(MAX_CONNECTIONS);
        config.setBlockWhenExhausted(true);
        jedisPool = new JedisPool(config, HOST, PORT);
    }

    /**
     * This Method Returns a connection object from connection pool.
     *
     * @return Jedis Object
     */
    public static Jedis getConnection() {
        try {
            Jedis jedis = jedisPool.getResource();
            if (INDEX > 0)
                jedis.select(INDEX);
            return jedis;
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Returning Redis Cache Connection Object to Pool.", e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_CONNECTION_ERROR.name(), e.getMessage());
        }
    }

    /**
     * This Method takes a connection object and put it back to pool.
     *
     * @param jedis
     */
    public static void returnConnection(Jedis jedis) {
        try {
            if (null != jedis) {
                jedisPool.returnResource(jedis);
            }
        } catch (Exception e) {
            TelemetryManager.error("Exception Occurred While Returning Redis Cache Connection Object to Pool.", e);
            throw new ServerException(CacheErrorCode.ERR_CACHE_CONNECTION_ERROR.name(), e.getMessage());
        }
    }
}
