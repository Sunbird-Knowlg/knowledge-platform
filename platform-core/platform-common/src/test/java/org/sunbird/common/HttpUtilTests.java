package org.sunbird.common;

import org.junit.Test;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HttpUtilTests {

    HttpUtil httpUtil = new HttpUtil();

    @Test
    public void testGetMetadataValidUrl() throws Exception {
        Map<String, Object> metadata = httpUtil.getMetadata("https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1130384356456120321307/3point4gb.mp4", new HashMap<>());
        assertNotNull(metadata);
        assertNotNull(metadata.get("Content-Type"));
        assertNotNull(metadata.get("Content-Length"));
        assertTrue(metadata.get("Content-Length") instanceof Number);
        assertTrue(metadata.get("Content-Type") instanceof String);
    }


    @Test(expected = ClientException.class)
    public void testGetMetadataInvalidUrl() throws Exception {
       httpUtil.getMetadata("https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_113038435645612032130743/3point4gb.mp4", new HashMap<>());
    }
}
