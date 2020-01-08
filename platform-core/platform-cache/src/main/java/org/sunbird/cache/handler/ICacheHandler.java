package org.sunbird.cache.handler;

/**
 * Contract for Cache Handler
 *
 * @author Kumar Gauraw
 */
public interface ICacheHandler {
    public abstract Object execute(String operation, String cacheKey, String objectKey);
}
