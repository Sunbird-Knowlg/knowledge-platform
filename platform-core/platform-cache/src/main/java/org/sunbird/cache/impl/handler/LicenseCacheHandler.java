package org.sunbird.cache.impl.handler;

import org.sunbird.cache.handler.ICacheHandler;


public class LicenseCacheHandler implements ICacheHandler {
    @Override
    public Object execute(String operation, String cacheKey, String objectKey) {
        Object cacheObject = null;
        switch (operation.toLowerCase()) {
            case "READ_LIST" : {
                System.out.println("Handling read list operation failure");
                break;
            }
            case "READ_STRING" : {
                System.out.println("Handling read string operation failure");
                break;
            }
            default: {
                break;
            }
        }
        return cacheObject;
    }
}
