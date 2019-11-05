package org.sunbird.schema;

import org.sunbird.schema.impl.JsonSchemaValidator;

import java.util.HashMap;
import java.util.Map;

public class SchemaValidatorFactory {

    private static Map<String, ISchemaValidator> schemaMap = new HashMap<String, ISchemaValidator>();

    public static ISchemaValidator getInstance(String name, String version) throws Exception {
        String key = getKey(name, version);
        if (schemaMap.containsKey(key)) {
            return schemaMap.get(key);
        } else {
            return initSchema(name, version);
        }
    }

    public static String getExternalStoreName(String name, String version) throws Exception {
        ISchemaValidator schemaValidator = SchemaValidatorFactory.getInstance(name, version);
        return schemaValidator.getConfig().getString("externalStoreName");
    }

    private static ISchemaValidator initSchema(String name, String version) throws Exception {
        ISchemaValidator schema = new JsonSchemaValidator(name, version);
        schemaMap.put(getKey(name, version), schema);
        return schema;
    }

    private static String getKey(String name, String version) {
        return name +":"+version;
    }
}
