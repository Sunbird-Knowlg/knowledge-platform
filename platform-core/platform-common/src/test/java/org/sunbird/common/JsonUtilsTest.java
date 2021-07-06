package org.sunbird.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.dto.ResponseParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonUtilsTest {

    @Test
    public void testSerializeValidObject() throws Exception {
        String serializedMap = JsonUtils.serialize(new HashMap<>() {{
            put("identifier", "do_1234");
            put("versionKey", "1234");
        }});
        Assert.assertNotNull(serializedMap);
        Assert.assertEquals("{\"identifier\":\"do_1234\",\"versionKey\":\"1234\"}",serializedMap);
    }

    @Test(expected = InvalidDefinitionException.class)
    public void testSerializeInvalidObject() throws Exception {
        JsonUtils.serialize(new Object());
    }

    @Test
    public void testDeserializeValidString() throws Exception {
        Map<String, Object> deserialized = JsonUtils.deserialize("{\"identifier\":\"do_1234\",\"versionKey\":\"1234\"}", Map.class);

        Assert.assertNotNull(deserialized);
        Assert.assertEquals(deserialized.size(), 2);
        Assert.assertEquals("do_1234", deserialized.get("identifier"));
        Assert.assertEquals("1234", deserialized.get("versionKey"));
    }

    @Test(expected = MismatchedInputException.class)
    public void testDeserializeInValidString() throws Exception {
        JsonUtils.deserialize("", Map.class);
    }

    @Test(expected = JsonParseException.class)
    public void testDeserializeCorruptString() throws Exception {
        JsonUtils.deserialize("{\"identifier\":\"do_1234\",\"versionKey\":\"1234\",}", Map.class);
    }

    @Test
    public void testConvertValidData() throws Exception {
        Map<String, Object> deserialized = JsonUtils.deserialize(    "{\n"+
                "    \"id\": \"api.user.courses.list\",\n"+
                "    \"ver\": \"v1\",\n"+
                "    \"ts\": \"2020-08-12 08:38:50:415+0000\",\n"+
                "    \"params\": {\n"+
                "        \"resmsgid\": null,\n"+
                "        \"msgid\": \"71ca0432-1320-4dc3-a7db-2d72b6e3fc46\",\n"+
                "        \"err\": null,\n"+
                "        \"status\": \"success\",\n"+
                "        \"errmsg\": null\n"+
                "    },\n"+
                "    \"responseCode\": \"OK\",\n"+
                "    \"result\": {" +
                "}" +
                "}", Map.class);

        Assert.assertNotNull(deserialized);
        ResponseParams responseParams = JsonUtils.convert((Map<String, Object>)deserialized.get("params"), ResponseParams.class );
        Assert.assertNotNull(responseParams);
        Assert.assertEquals("71ca0432-1320-4dc3-a7db-2d72b6e3fc46", responseParams.getMsgid());

    }

    @Test
    public void testConvertJsonStringValid_1() throws Exception {
        Map<String, Object> deserialized = (Map<String, Object>) JsonUtils.convertJSONString("{\"identifier\":\"do_1234\",\"versionKey\":\"1234\"}");
        Assert.assertNotNull(deserialized);
        Assert.assertEquals(deserialized.size(), 2);
        Assert.assertEquals("do_1234", deserialized.get("identifier"));
        Assert.assertEquals("1234", deserialized.get("versionKey"));
    }

    @Test
    public void testConvertJsonStringValid_2() throws Exception {
        List<String> list = (List<String>) JsonUtils.convertJSONString("[\"test1\",\"test2\"]");
        Assert.assertNotNull(list);
        Assert.assertEquals(list.size(), 2);
        Assert.assertEquals("test1", list.get(0));
        Assert.assertEquals("test2", list.get(1));
    }

    @Test
    public void testConvertJsonStringInvalid() throws Exception {
        List<String> list = (List<String>) JsonUtils.convertJSONString("\"test1\"");
        Assert.assertNull(list);

    }
}
