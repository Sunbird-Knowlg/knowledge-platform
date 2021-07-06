package org.sunbird.common.exception;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ClientExceptionTest {
    @Test(expected = ClientException.class)
    public void testClientException_1() throws Exception {
        throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please Provide Valid File Name.");
    }

    @Test(expected = ClientException.class)
    public void testClientException_2() throws Exception {
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Please Provide Valid File Name.", Arrays.asList("Message one ", "message 2"));
    }

    @Test(expected = ClientException.class)
    public void testClientException_3() throws Exception {
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Please Provide Valid File Name.", "message1", "message2");
    }

    @Test(expected = ClientException.class)
    public void testClientException_4() throws Exception {
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Please Provide Valid File Name.", new Throwable("message throwable"), "message1", "message2");

    }

    @Test(expected = ClientException.class)
    public void testClientException_5() throws Exception {
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Please Provide Valid File Name.", new Throwable("message throwable"));
    }

    @Test
    public void testClientException_6() throws Exception {
        ClientException exception =  new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Please Provide Valid File Name.", new Throwable("message throwable"));
        Assert.assertEquals(ResponseCode.CLIENT_ERROR, exception.getResponseCode());
    }
}
