package org.sunbird.telemetry;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.telemetry.handler.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelemetryGeneratorTest {

    @BeforeClass
    public static void init() {

    }

    @Test
    public void testAccessTelemetry() throws Exception {
        String accessLog = TelemetryGenerator.access(getContext(), getParams());
        Assert.assertNotNull(accessLog);
    }

    @Test
    public void testLog_1() throws Exception {
        String event = TelemetryGenerator.log(getContext(), "payload",
                Level.INFO.name(), "This is an info log", "1234",
                getParams());
        Assert.assertNotNull(event);
    }

    @Test
    public void testLog_2() throws Exception {
        String event = TelemetryGenerator.log(getContext(), "payload",
                Level.INFO.name(), "This is an info log");
        Assert.assertNotNull(event);
    }

    @Test
    public void testError_1() throws Exception {
        String event = TelemetryGenerator.error(getContext(), "ERR_INVALID_DATA",
                Level.ERROR.name(), getStacktrace());
        Assert.assertNotNull(event);
    }

    @Test
    public void testError_2() throws Exception {
        String event = TelemetryGenerator.error(getContext(), "ERR_INVALID_DATA",
                Level.ERROR.name(), getStacktrace(), "1234", Arrays.asList("object"));
        Assert.assertNotNull(event);
    }

    @Test
    public void testAudit_1() throws Exception {
        String event = TelemetryGenerator.audit(getContext(), Arrays.asList("identifier", "status"), "Review", "Draft");
        Assert.assertNotNull(event);
    }

    @Test
    public void testAudit_2() throws Exception {
        String event = TelemetryGenerator.audit(getContext(), Arrays.asList("identifier", "status"), "Review", "Draft", getCdata());
        Assert.assertNotNull(event);
    }


    private Map<String, String> getContext() {
        return new HashMap<>() {{
            put(TelemetryParams.ENV.name(), "TEST_ENV");
            put(TelemetryParams.CHANNEL.name(), "TEST_CHANNEL");
            put("sid", "37948134149401");
            put("did", "mac");
            put(TelemetryParams.APP_ID.name(), "mac-app");
            put("duration", "318361274");
            put("objectId", "CONTENT");
            put("objectType", "Content");
            put("pkgVersion", "2");
        }};
    }

    private Map<String, Object> getParams() {
        return new HashMap<>() {{
            put("identifier", "do_1234");
            put("status", "Draft");
            put("versionKey", "37948134149401");
            put("code", "mac-9319");
            put("contentType", "Resource");
        }};
    }

    private String getStacktrace() {
        return "java.lang.Throwable: A test exception\n" +
                "  at com.stackify.stacktrace.StackElementExample.methodD(StackElementExample.java:23)\n" +
                "  at com.stackify.stacktrace.StackElementExample.methodC(StackElementExample.java:15)\n" +
                "  at com.stackify.stacktrace.StackElementExampleTest\n" +
                "    .whenElementOneIsReadUsingThrowable_thenMethodCatchingThrowableIsObtained(StackElementExampleTest.java:34)";
    }

    private List<Map<String, Object>> getCdata() {
        return new ArrayList<Map<String, Object>>() {
            {
                add(new HashMap<>() {
                    {
                        put("identifier", "do_1234");
                        put("status", "Draft");
                        put("versionKey", "37948134149401");
                        put("code", "mac-9319");
                        put("contentType", "Resource");
                    }
                });

            }
        };
    }


}
