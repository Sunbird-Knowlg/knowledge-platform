package org.sunbird.telemetry.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestLogAsyncGraphEvent {

    @Test
    public void testPushMessageToLogger() {
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>() {{
            add(new HashMap<>() {{
                put("message", "test");
            }});
        }};
        LogAsyncGraphEvent.pushMessageToLogger(messages);
    }
}
