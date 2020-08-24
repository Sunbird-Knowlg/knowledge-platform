package org.sunbird.common.exception;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class MiddlewareExceptionTest {
    @Test(expected = MiddlewareException.class)
    public void testMiddlewareException_1() throws Exception {
        throw new MiddlewareException(ResponseCode.OK.name(), "Please Provide Valid File Name.");
    }

    @Test(expected = MiddlewareException.class)
    public void testMiddlewareException_2() throws Exception {
        throw new MiddlewareException(ResponseCode.PARTIAL_SUCCESS.name(), "Please Provide Valid File Name.", Arrays.asList("Message one ", "message 2"));
    }

    @Test(expected = MiddlewareException.class)
    public void testMiddlewareException_3() throws Exception {
        throw new MiddlewareException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Please Provide Valid File Name.", "message1", "message2");
    }

    @Test(expected = MiddlewareException.class)
    public void testMiddlewareException_4() throws Exception {
        throw new MiddlewareException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Please Provide Valid File Name.", new Throwable("message throwable"), "message1", "message2");

    }

    @Test(expected = MiddlewareException.class)
    public void testMiddlewareException_5() throws Exception {
        throw new MiddlewareException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Please Provide Valid File Name.", new Throwable("message throwable"));
    }

    @Test
    public void testMiddlewareException_6() throws Exception {
       MiddlewareException exception = new MiddlewareException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Please Provide Valid File Name.", new Throwable("message throwable"));
        Assert.assertEquals(ResponseCode.SERVER_ERROR, exception.getResponseCode());
        Assert.assertEquals("ERR_SYSTEM_EXCEPTION", exception.getErrCode());
        Assert.assertEquals(null, exception.getMessages());
    }

}
