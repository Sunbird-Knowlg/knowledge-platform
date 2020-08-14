package org.sunbird.common.exception;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ServerExceptionTest {
    @Test(expected = ServerException.class)
    public void testServerException_1() throws Exception {
        throw new ServerException(ResponseCode.SERVER_ERROR.name(), "Failed to create node object. Node from database is null.");
    }

    @Test(expected = ServerException.class)
    public void testServerException_2() throws Exception {
        throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Failed to create node object. Node from database is null.", Arrays.asList("Message one ", "message 2"));
    }

    @Test(expected = ServerException.class)
    public void testServerException_3() throws Exception {
        throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Failed to create node object. Node from database is null.", "message1", "message2");
    }

    @Test(expected = ServerException.class)
    public void testServerException_4() throws Exception {
        throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Failed to create node object. Node from database is null.", new Throwable("message throwable"), "message1", "message2");

    }

    @Test(expected = ServerException.class)
    public void testServerException_5() throws Exception {
        throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Failed to create node object. Node from database is null.", new Throwable("message throwable"));
    }

    @Test
    public void testServerException_6() throws Exception {
        ServerException exception =  new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name(), "Failed to create node object. Node from database is null.", new Throwable("message throwable"));
        Assert.assertEquals(ResponseCode.SERVER_ERROR, exception.getResponseCode());
    }
}
