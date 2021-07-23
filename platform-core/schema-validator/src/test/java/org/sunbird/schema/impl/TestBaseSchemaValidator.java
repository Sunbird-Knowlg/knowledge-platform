package org.sunbird.schema.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.Whitebox;
import org.sunbird.schema.ISchemaValidator;
import org.sunbird.schema.SchemaValidatorFactory;
import org.sunbird.schema.dto.ValidationResult;

import java.lang.reflect.Method;
import java.util.*;

@PrepareForTest({Config.class})
public class TestBaseSchemaValidator {

    static ISchemaValidator validator;
    ObjectMapper mapper = new ObjectMapper();
    private BaseSchemaValidator baseSchemaValidator;

    @BeforeClass
    public static void init(){
        try{
            validator = SchemaValidatorFactory.getInstance("content","1.0");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetJsonProps() {
        try{
            List<String> jsonProps = validator.getJsonProps();
            Assert.assertNotNull(jsonProps);
            Assert.assertFalse(jsonProps.isEmpty());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Test
    public void testGetStructuredData() throws Exception {
        mockClass();
        Mockito.doCallRealMethod().when(baseSchemaValidator).getStructuredData(getInput());
        ValidationResult result = baseSchemaValidator.getStructuredData(getInput());
        Assert.assertTrue(MapUtils.isNotEmpty(result.getMetadata()));
        Assert.assertTrue(StringUtils.equals((String) result.getMetadata().get("name"), "c-12"));
    }

    @Test
    public void testCleanEmptyKeys() throws Exception {
        mockClass();
        Method cleanEmptyKeys = BaseSchemaValidator.class.getDeclaredMethod("cleanEmptyKeys", Map.class);
        cleanEmptyKeys.setAccessible(true);
        Map<String, Object> inputMap = getInput();
        inputMap.put("emptyString", "");
        inputMap.put("emptyMap", new HashMap<>());
        Map<String, Object> resultMap = (Map<String, Object>) cleanEmptyKeys.invoke(baseSchemaValidator, inputMap);
        Assert.assertTrue(MapUtils.isNotEmpty(resultMap));
        Assert.assertTrue(!resultMap.containsKey("emptyString"));
        Assert.assertTrue(!resultMap.containsKey("emptyMap"));
    }

    @Test
    public void testGetAllProps() {
        try{
            List<String> jsonProps = validator.getAllProps();
            Assert.assertNotNull(jsonProps);
            Assert.assertFalse(jsonProps.isEmpty());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public Map<String, Object> getInput() throws Exception {
        Map<String, Object> input = mapper.readValue("{\"contentType\":\"Resource\",\"name\":\"c-12\",\"code\":\"c-12\",\"mimeType\":\"application/pdf\",\"tags\":[\"colors\",\"games\"],\"subject\":[\"Hindi\",\"English\"],\"medium\":[\"Hindi\",\"English\"],\"channel\":\"in.ekstep\",\"osId\":\"org.ekstep.quiz.app\",\"contentEncoding\":\"identity\",\"contentDisposition\":\"inline\"}", Map.class);
        return input;
    }

    public void mockClass() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.hasPath("relations")).thenReturn(true);
        baseSchemaValidator = Mockito.mock(BaseSchemaValidator.class);
        Whitebox.setInternalState(baseSchemaValidator, "name", "version");
        Mockito.when(baseSchemaValidator.getConfig()).thenReturn(config);
        ConfigObject obj = Mockito.mock(ConfigObject.class);
        Mockito.when(baseSchemaValidator.getConfig().getObject("relations")).thenReturn(obj);
    }
}
