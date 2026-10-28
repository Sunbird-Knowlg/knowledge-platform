package org.sunbird.telemetry.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.RequestParams;
import org.sunbird.telemetry.dto.TelemetryBEEvent;

import java.util.HashMap;
import java.util.Map;

public class TestLogTelemetryEventUtil {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testLogInstructionEvent() throws Exception {
        Map<String, Object> actor = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> object = new HashMap<>();
        Map<String, Object> edata = new HashMap<>();
        String jsonMessage = LogTelemetryEventUtil.logInstructionEvent(actor, context, object, edata);
        Assert.assertTrue(StringUtils.isNotEmpty(jsonMessage));
        Assert.assertTrue(StringUtils.isNotEmpty((String) mapper.readValue(jsonMessage, Map.class).get("mid")));
    }

    @Test
    public void testLogContentSearchEvent() throws Exception {
        String query = "";
        Object filters = new HashMap<>();
        Object sort = new HashMap<>();
        String correlationId = "";
        int size = 0;
        Request req = getReq();
        String jsonMessage = LogTelemetryEventUtil.logContentSearchEvent(query, filters, sort, correlationId, size, req);
        Assert.assertTrue(StringUtils.isNotEmpty(jsonMessage));
        Assert.assertTrue(StringUtils.isNotEmpty((String) mapper.readValue(jsonMessage, Map.class).get("mid")));
    }

    @Test
    public void testLogContentSearchEventWithEmptyValue() throws Exception {
        String query = "";
        Object filters = new Object();
        Object sort = new Object();
        String correlationId = "";
        int size = 0;
        Request req = getReqWithoutDid();
        String jsonMessage = LogTelemetryEventUtil.logContentSearchEvent(query, filters, sort, correlationId, size, req);
        Assert.assertTrue(StringUtils.isEmpty(jsonMessage));
    }

    @Test
    public void testGetMD5Hash() throws Exception {
        TelemetryBEEvent event = new TelemetryBEEvent() {{
            setEid("test");
            setEts(12345L);
        }};
        Map<String, Object> data = new HashMap<>() {{
            putAll(JsonUtils.deserialize("{\"id\":\"testid\",\"state\":\"teststate\",\"prevstate\":\"testprevstate\"}", Map.class));
        }};
        String messageId = LogTelemetryEventUtil.getMD5Hash(event, data);
        Assert.assertTrue(StringUtils.isNotEmpty(messageId));
    }

    private Request getReq() throws Exception {
        return new Request() {{
            setParams(JsonUtils.convert(JsonUtils.deserialize("{\"cid\":\"testcid\",\"uid\":\"testuid\",\"sid\":\"testsid\",\"did\":\"testdid\",\"sid\":\"testsid\"}", Map.class), RequestParams.class));
        }};
    }

    private Request getReqWithoutDid() throws Exception {
        return new Request() {{
            setParams(JsonUtils.convert(JsonUtils.deserialize("{\"cid\":\"testcid\",\"uid\":\"testuid\",\"sid\":\"testsid\",\"sid\":\"testsid\"}", Map.class), RequestParams.class));
        }};
    }
}
