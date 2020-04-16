package org.sunbird.search.util;

import org.sunbird.schema.ISchemaValidator;
import org.sunbird.schema.SchemaValidatorFactory;

import java.util.HashMap;
import java.util.Map;

public class DefinitionUtil {
    
    private static final Map<String, Map<String, String>> objectTypeSchemaMap = new HashMap<String, Map<String, String>>(){{
       put("content", new HashMap<String, String>(){{ put("schemaName", "content"); put("version", "1.0");}});
       put("categoryinstance", new HashMap<String, String>(){{ put("schemaName", "categoryInstance"); put("version", "1.0");}});
       put("channel", new HashMap<String, String>(){{ put("schemaName", "channel"); put("version", "1.0");}});
       put("framework", new HashMap<String, String>(){{ put("schemaName", "framework"); put("version", "1.0");}});
       put("itemset", new HashMap<String, String>(){{ put("schemaName", "itemset"); put("version", "2.0");}});
       put("license", new HashMap<String, String>(){{ put("schemaName", "license"); put("version", "1.0");}});
    }};
    
    
    public static Map<String, Object> getMetaData(String objectType) throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        String schemaName = objectTypeSchemaMap.get(objectType.toLowerCase()).getOrDefault("schemaName", "content");
        String version = objectTypeSchemaMap.get(objectType.toLowerCase()).getOrDefault("version", "1.0");
        ISchemaValidator schemaValidator = SchemaValidatorFactory.getInstance(schemaName, version);
        if(schemaValidator.getConfig().hasPath("searchProps")){
            metadata.putAll(schemaValidator.getConfig().getObject("searchProps"));
        }
        return metadata;
    } 
}
