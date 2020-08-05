package org.sunbird.telemetry.logger;

import org.junit.Test;
import org.sunbird.common.exception.MiddlewareException;

public class TestTelemetryManager {

    @Test
    public void testError() {
        Throwable e = new MiddlewareException("500", "message");
        Object object = new Object();
        TelemetryManager.error("message", e, object);
    }
}
