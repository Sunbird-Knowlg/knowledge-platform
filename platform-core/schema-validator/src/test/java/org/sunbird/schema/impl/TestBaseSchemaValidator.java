package org.sunbird.schema.impl;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sunbird.schema.ISchemaValidator;
import org.sunbird.schema.SchemaValidatorFactory;

import java.util.List;

public class TestBaseSchemaValidator {

    static ISchemaValidator validator;

    @BeforeClass
    public static void init(){
        try{
            validator = SchemaValidatorFactory.getInstance("content","1.0");
        }catch(Exception e){
            e.printStackTrace();
        }
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
}
