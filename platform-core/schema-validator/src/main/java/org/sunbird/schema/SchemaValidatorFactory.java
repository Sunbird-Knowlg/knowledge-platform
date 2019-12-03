package org.sunbird.schema;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.schema.impl.JsonSchemaValidator;

import java.util.HashMap;
import java.util.List;
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

    public static ISchemaValidator getInstance(String name, String version, String fallback) throws Exception {
        String key = getKey(name, version);
        key += ":" + fallback;
        if (schemaMap.containsKey(key)) {
            return schemaMap.get(key);
        } else {
            return initSchema(name, version, fallback);
        }
    }

    public static String getExternalStoreName(String name, String version) throws Exception {
        ISchemaValidator schemaValidator = SchemaValidatorFactory.getInstance(name, version);
        String keyspaceName = Platform.config.getString(name + ".keyspace");
        if(StringUtils.isEmpty(keyspaceName))
            throw new ServerException("ERR_KEYSPACE_NOT_DEFINED", "Key space for " + name + " is not configured.");
        return keyspaceName + "." + schemaValidator.getConfig().getString("external.tableName");
    }

    public static List<String> getExternalPrimaryKey(String name, String version) throws Exception {
        ISchemaValidator schemaValidator = SchemaValidatorFactory.getInstance(name, version);
        return schemaValidator.getConfig().getStringList("external.primaryKey");
    }

    private static ISchemaValidator initSchema(String name, String version) throws Exception {
        ISchemaValidator schema = new JsonSchemaValidator(name, version);
        schemaMap.put(getKey(name, version), schema);
        return schema;
    }

    private static ISchemaValidator initSchema(String name, String version, String fallback) throws Exception {
        ISchemaValidator schema = new JsonSchemaValidator(name, version, fallback);
        schemaMap.put(getKey(name, version), schema);
        return schema;
    }

    private static String getKey(String name, String version) {
        return StringUtils.joinWith(":", name, version);
    }

    private static String getKey(String name, String version, String fallback) {
        return StringUtils.joinWith(":", name, version, fallback);
    }
}
