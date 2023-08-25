package org.sunbird.common.dto;
import org.junit.Assert;
import org.junit.Test;

public class RequestTest {
    @Test
    public void testGetRequestString() {
        Request request = new Request();
        String key = "testKey";
        String value = "testValue";
        request.put(key, value);
        String result = request.getRequestString(key, "");
        Assert.assertEquals(value, result);
    }

    @Test
    public void testGetRequestStringWithDefaultValue() {
        Request request = new Request();
        String result = request.getRequestString("nonExistentKey", "");
        Assert.assertEquals("", result);
    }
}
