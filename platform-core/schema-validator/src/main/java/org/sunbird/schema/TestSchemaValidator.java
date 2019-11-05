package org.sunbird.schema;

import org.sunbird.common.JsonUtils;
import org.sunbird.schema.dto.ValidationResult;

import java.util.Map;

public class TestSchemaValidator {

    public static void main(String args[]) throws Exception {
        ISchemaValidator schemaValidator = SchemaValidatorFactory.getInstance("Content", "1.0");
        ValidationResult result = schemaValidator.validate(JsonUtils.deserialize("{\"name\": \"Mahesh\"}", Map.class));
        System.out.println("Result: " + result);
    }
}
