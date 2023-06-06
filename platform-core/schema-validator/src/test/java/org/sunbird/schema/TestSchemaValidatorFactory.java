package org.sunbird.schema;

import com.typesafe.config.ConfigException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.sunbird.common.exception.ServerException;

import javax.json.JsonException;
import java.util.Arrays;
import java.util.List;

@Ignore
public class TestSchemaValidatorFactory {


    @BeforeClass
    public static void init() {

    }

    @Test
    public void testGetInstance() throws Exception {
        Assert.assertNotNull(SchemaValidatorFactory.getInstance("content", "1.0"));
    }

    @Test (expected = JsonException.class)
    public void testGetInstanceInvalidSchema() throws Exception {
        SchemaValidatorFactory.getInstance("content", "2.0");
    }


    @Test (expected = JsonException.class)
    public void testGetInstanceInvalidFallbackSchema() throws Exception {
        SchemaValidatorFactory.getInstance("content", "3.0", "2.0");
    }

    @Test
    public void getExternalStoreName() throws Exception {
        String actual = SchemaValidatorFactory.getExternalStoreName("content", "1.0");
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual, "content_store.content_data");
    }

    @Test(expected = ServerException.class)
    public void getExternalStoreNameInvalid() throws Exception {
        SchemaValidatorFactory.getExternalStoreName("category", "1.0");
    }

    @Test
    public void getExternalPrimaryKey() throws Exception{
        List<String> actual = SchemaValidatorFactory.getExternalPrimaryKey("content", "1.0");
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual, Arrays.asList("content_id"));
    }

    @Test(expected = ConfigException.class)
    public void getExternalPrimaryKeyInvalid() throws Exception{
        SchemaValidatorFactory.getExternalPrimaryKey("category", "1.0");
    }

}
