package org.sunbird.telemetry.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.RequestParams;
import org.sunbird.common.dto.Response;

import java.util.HashMap;
import java.util.Map;

public class TestTelemetryAccessEventUtil {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testWriteTelemetryEventLog() throws Exception {
        Map<String, Object> data = getDataMap();
        TelemetryAccessEventUtil.writeTelemetryEventLog(data);
    }

    @Test
    public void testWriteTelemetryEventLogWithHeaders() throws Exception {
        Map<String, Object> data = getDataMapWithHeaders();
        TelemetryAccessEventUtil.writeTelemetryEventLog(data);
    }

    private Map<String, Object> getDataMap() throws Exception {
        Map<String, Object> data = new HashMap<String, Object>() {{
            put("Request", new Request() {{
                setParams(JsonUtils.convert(JsonUtils.deserialize("{\"cid\":\"testcid\",\"uid\":\"testuid\",\"sid\":\"testsid\",\"did\":\"testdid\",\"sid\":\"testsid\"}", Map.class), RequestParams.class));
            }});
            put("Response", new Response() {{
                setId("ekstep.learning.categoryinstance.read");
            }});
            putAll(mapper.readValue("{\"path\":\"/content/v3/create\",\"RemoteAddress\":\"0:0:0:0:0:0:0:1\",\"Method\":\"POST\",\"X-Channel-Id\":\"in.ekstep\",\"Protocol\":\"http\",\"env\":\"content\",\"Status\":500}", Map.class));
            put("ContentLength", 307L);
            put("StartTime", 1596620754800L);
            put("APP_ID", "app-id");
        }};
        return data;
    }

    private Map<String, Object> getDataMapWithHeaders() throws Exception {
        Map<String, Object> data = getDataMap();
        data.putAll(mapper.readValue("{\"X-Session-ID\":\"testsid\",\"X-Consumer-ID\":\"testcid\",\"X-Device-ID\":\"testdid\",\"X-Authenticated-Userid\":\"testuid\"}", Map.class));
        ((Response) data.get("Response")).setId("test");
        return data;
    }
}
