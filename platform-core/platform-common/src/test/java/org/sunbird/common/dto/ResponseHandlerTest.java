package org.sunbird.common.dto;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;

import java.util.Arrays;

public class ResponseHandlerTest {

    @Test
    public void handleResponses_1() throws Exception {
        Response finalResponse = ResponseHandler.handleResponses(Arrays.asList(getServerErrorResponse(), getPartialSuccessResponse(), getClientErrorResponse(), getSuccessResponse()));
        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(finalResponse.getResponseCode(), ResponseCode.SERVER_ERROR);
    }

    @Test
    public void handleResponses_2() throws Exception {
        Response finalResponse = ResponseHandler.handleResponses(Arrays.asList(getSuccessResponse(), getPartialSuccessResponse(), getClientErrorResponse(), getSuccessResponse()));
        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(finalResponse.getResponseCode(), ResponseCode.PARTIAL_SUCCESS);
    }

    @Test
    public void handleResponses_3() throws Exception {
        Response finalResponse = ResponseHandler.handleResponses(Arrays.asList(getSuccessResponse(), getSuccessResponse(), getClientErrorResponse(), getSuccessResponse()));
        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(finalResponse.getResponseCode(), ResponseCode.CLIENT_ERROR);
    }


    @Test
    public void handleResponses_4() throws Exception {
        Response finalResponse = ResponseHandler.handleResponses(Arrays.asList(getSuccessResponse(), getSuccessResponse(), getSuccessResponse(), getSuccessResponse()));
        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(finalResponse.getResponseCode(), ResponseCode.OK);
    }

    @Test
    public void handleResponses_5() throws Exception {
        Response finalResponse = ResponseHandler.handleResponses(Arrays.asList(getSuccessResponse(), getResourceNotFoundResponse(), getSuccessResponse(), ResponseHandler.OK()));
        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(finalResponse.getResponseCode(), ResponseCode.RESOURCE_NOT_FOUND);
    }

    @Test
    public void handleResponses_6() throws Exception {
        Boolean isError = ResponseHandler.checkError(getServerErrorResponse());
        Assert.assertTrue(isError);
    }

    @Test
    public void handleResponses_7() throws Exception {
        Boolean isError = ResponseHandler.checkError(getSuccessResponse());
        Assert.assertTrue(!isError);
    }

    @Test
    public void handleResponses_8() throws Exception {
        String message = ResponseHandler.getErrorMessage(getServerErrorResponse());
        Assert.assertNotNull(message);
        Assert.assertEquals("Something went wrong in server while processing the request", message);
    }

    @Test
    public void handleResponses_9() throws Exception {
        String message = ResponseHandler.getErrorMessage(new Response());
        Assert.assertNull(message);
    }

    @Test
    public void handleResponses_10() throws Exception {
        Boolean flag = ResponseHandler.isResponseNotFoundError(getResourceNotFoundResponse());
        Assert.assertTrue(flag);
    }

    @Test
    public void handleResponses_11() throws Exception {
        Boolean flag = ResponseHandler.isResponseNotFoundError(getClientErrorResponse());
        Assert.assertFalse(flag);
    }

    @Test
    public void handleResponses_12() throws Exception {
        Throwable e = new ClientException("CLIENT_ERROR","Metadata mimeType should be one of: [application/vnd.ekstep.content-collection]");
        Response response = ResponseHandler.getErrorResponse(e);
        Assert.assertNotNull(response);
    }

    @Test
    public void handleResponses_13() throws Exception {
        Throwable e = new ClientException("SERVER_ERROR","Metadata mimeType should be one of: [application/vnd.ekstep.content-collection]");
        Response response = ResponseHandler.getErrorResponse(e);
        Assert.assertNotNull(response);
    }

    private Response getServerErrorResponse() throws Exception {
        return JsonUtils.deserialize("{\n" +
                "    \"id\": \"api.content.create\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-09-30T09:22:32ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"fc7e0a7b-7add-4a0d-a805-a8932336667e\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": \"ERR_SYSTEM_EXCEPTION\",\n" +
                "        \"status\": \"failed\",\n" +
                "        \"errmsg\": \"Something went wrong in server while processing the request\"\n" +
                "    },\n" +
                "    \"responseCode\": \"SERVER_ERROR\",\n" +
                "    \"result\": {}\n" +
                "}", Response.class);
    }

    private Response getSuccessResponse() throws Exception {
        return JsonUtils.deserialize("{\n" +
                "    \"id\": \"api.content.create\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-09-30T09:21:10ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"be55769c-6d31-4667-9b5c-4b35d7b85c23\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"identifier\": \"do_1131191412394393601663\",\n" +
                "        \"node_id\": \"do_1131191412394393601663\",\n" +
                "        \"versionKey\": \"1601457670834\"\n" +
                "    }\n" +
                "}", Response.class);    }

    private Response getPartialSuccessResponse() throws Exception {
        return JsonUtils.deserialize("{\n" +
                "    \"id\": \"api.content.create\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-09-30T09:25:55ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"9ed49194-2f17-4564-afa8-ef7582a34117\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": \"PARTIAL_SUCCESS\",\n" +
                "        \"status\": \"failed\",\n" +
                "        \"errmsg\": \"Validation Errors\"\n" +
                "    },\n" +
                "    \"responseCode\": \"PARTIAL_SUCCESS\",\n" +
                "    \"result\": {\n" +
                "        \"messages\": [\n" +
                "            \"Metadata mimeType should be one of: [application/vnd.ekstep.content-collection]\"\n" +
                "        ]\n" +
                "    }\n" +
                "}", Response.class);
    }

    private Response getClientErrorResponse() throws Exception {
        return JsonUtils.deserialize("{\n" +
                "    \"id\": \"api.content.create\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-09-30T09:25:55ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"9ed49194-2f17-4564-afa8-ef7582a34117\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": \"CLIENT_ERROR\",\n" +
                "        \"status\": \"failed\",\n" +
                "        \"errmsg\": \"Validation Errors\"\n" +
                "    },\n" +
                "    \"responseCode\": \"CLIENT_ERROR\",\n" +
                "    \"result\": {\n" +
                "        \"messages\": [\n" +
                "            \"Metadata mimeType should be one of: [application/vnd.ekstep.content-collection]\"\n" +
                "        ]\n" +
                "    }\n" +
                "}", Response.class);
    }

    private Response getResourceNotFoundResponse() throws Exception {
        return JsonUtils.deserialize("{\n" +
                "    \"id\": \"api.content.hierarchy.get\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-09-30T09:34:19ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"09649a91-28a8-4389-afb4-f6528c5c61be\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": \"RESOURCE_NOT_FOUND\",\n" +
                "        \"status\": \"failed\",\n" +
                "        \"errmsg\": \"rootId do_1131177453355356814 does not exist\"\n" +
                "    },\n" +
                "    \"responseCode\": \"RESOURCE_NOT_FOUND\",\n" +
                "    \"result\": {}\n" +
                "}", Response.class);
    }

}
