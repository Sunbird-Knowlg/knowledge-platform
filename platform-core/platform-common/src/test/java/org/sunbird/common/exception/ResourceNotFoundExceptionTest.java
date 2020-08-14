package org.sunbird.common.exception;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ResourceNotFoundExceptionTest {
    @Test(expected = ResourceNotFoundException.class)
    public void testResourceNotFoundException_1() throws Exception {
        throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ");
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testResourceNotFoundException_2() throws Exception {
        throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ", Arrays.asList("Message one ", "message 2"));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testResourceNotFoundException_3() throws Exception {
        throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ", "message1", "message2");
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testResourceNotFoundException_4() throws Exception {
        throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ", new Throwable("message throwable"), "message1", "message2");

    }

    @Test(expected = ResourceNotFoundException.class)
    public void testResourceNotFoundException_5() throws Exception {
        throw new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ", new Throwable("message throwable"));
    }

    @Test
    public void testResourceNotFoundException_6() throws Exception {
        ResourceNotFoundException exception =  new ResourceNotFoundException(ResponseCode.RESOURCE_NOT_FOUND.name(), "Node not found with identifier: ", "do_1234");
        Assert.assertEquals(ResponseCode.RESOURCE_NOT_FOUND, exception.getResponseCode());
        Assert.assertEquals("do_1234", exception.getIdentifier());
    }
}
