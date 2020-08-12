package org.sunbird.telemetry.logger;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.exception.MiddlewareException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestTelemetryManager {

    @Test
    public void testError() {
        Throwable e = new MiddlewareException("500", "message");
        Object object = new Object();
        TelemetryManager.error("message", e, object);
    }

    @Test
    public void testAudit() {
        List<String> props = new ArrayList<>();
        TelemetryManager.audit("id", "type", props, "state", "prevState");
    }

    @Test
    public void testSearch() {
        Map<String, Object> context = new HashMap<>();
        Object filters = new Object();
        Object sort = new Object();
        Object topN = new Object();
        TelemetryManager.search(context, "query", filters, sort, 1, topN, "type");
    }

    @Test
    public void testSearchWithContextData() {
        Map<String, Object> context = new HashMap<>() {{
            put("DEVICE_ID", "testdid");
            put("objectId", "testobjectId");
            put("objectType", "content");
        }};
        Object filters = new Object();
        Object sort = new Object();
        Object topN = new Object();
        TelemetryManager.search(context, "query", filters, sort, 1, topN, "type");
    }

    @Test
    public void testLog() throws Exception {
        Method method = TelemetryManager.class.getDeclaredMethod("log", String.class, Map.class, String.class);
        method.setAccessible(true);
        Map<String, Object> params = new HashMap<>();
        method.invoke(null, "message", params, "INFO");
    }

    @Test
    public void testGetContext() throws Exception {
        Method method = TelemetryManager.class.getDeclaredMethod("getContext");
        method.setAccessible(true);
        Map<String, Object> context = (Map<String, Object>) method.invoke(null);
        Assert.assertTrue(MapUtils.isNotEmpty(context));
        Assert.assertTrue(StringUtils.equals((String) context.get("ACTOR"), "org.sunbird.learning.platform"));
        Assert.assertTrue(StringUtils.equals((String) context.get("CHANNEL"), "in.ekstep"));
        Assert.assertTrue(StringUtils.equals((String) context.get("ENV"), "system"));
    }

    @Test
    public void testLogRequestBody() {
        TelemetryManager.logRequestBody("message");
    }
}
