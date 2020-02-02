package org.sunbird.schema.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonSchemaReaderFactory;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;
import org.leadpony.justify.api.ValidationConfig;
import org.leadpony.justify.internal.schema.BasicJsonSchema;
import org.sunbird.common.JsonUtils;
import org.sunbird.schema.ISchemaValidator;
import org.sunbird.schema.dto.ValidationResult;

import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import java.io.IOException;
import java.io.InputStream;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseSchemaValidator implements ISchemaValidator {

    public String name;
    public String version;
    protected static final JsonValidationService service = JsonValidationService.newInstance();
    public JsonSchema schema;
    protected JsonSchemaReaderFactory schemaReaderFactory;
    protected Config config;

    public BaseSchemaValidator(String name, String version) {
        this.name = name;
        this.version = version;
        this.schemaReaderFactory = service.createSchemaReaderFactoryBuilder()
//                .withSchemaResolver(this::resolveSchema)
                .build();

    }

    public abstract JsonSchema resolveSchema(URI id);

    /**
     *
     * @return
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Reads the JSON schemas from the specified path.
     *

     * @param stream the InputStream for the schema.
     * @return the read schema.
     */
    protected JsonSchema readSchema(InputStream stream) {
        try (JsonSchemaReader reader = schemaReaderFactory.createSchemaReader(stream)) {
            return reader.read();
        }
    }

    protected JsonSchema readSchema(Path path) {
        try (JsonSchemaReader reader = schemaReaderFactory.createSchemaReader(path)) {
            return reader.read();
        }
    }

    public ValidationResult getStructuredData(Map<String, Object> input) {
        Map<String, Object> relations = getRelations(input);
        Map<String, Object> externalData = getExternalProps(input);
        return new ValidationResult(input, relations, externalData);
    }

    public ValidationResult validate(Map<String, Object> data) throws Exception {

        String dataWithDefaults = withDefaultValues(JsonUtils.serialize(data));
        Map<String, Object> validationDataWithDefaults = cleanEmptyKeys(JsonUtils.deserialize(dataWithDefaults, Map.class));

        List<String> messages = validate(new StringReader(JsonUtils.serialize(validationDataWithDefaults)));
        Map<String, Object> dataMap = JsonUtils.deserialize(dataWithDefaults, Map.class);
        Map<String, Object> externalData = getExternalProps(dataMap);
        Map<String, Object> relations = getRelations(dataMap);
        return new ValidationResult(messages, dataMap, relations, externalData);
    }

    private Map<String, Object> cleanEmptyKeys(Map<String, Object> input) {
        return input.entrySet().stream().filter(entry -> {
            Object value = entry.getValue();
            if(value == null){
                return false;
            }else if(value instanceof String) {
                return StringUtils.isNotBlank((String) value);
            } else if (value instanceof List) {
                return CollectionUtils.isNotEmpty((List) value);
            } else if (value instanceof String[]) {
                return CollectionUtils.isNotEmpty(Arrays.asList((String[]) value));
            } else if(value instanceof Map[]) {
                return CollectionUtils.isNotEmpty(Arrays.asList((Map[])value));
            } else if (value instanceof Map) {
                return MapUtils.isNotEmpty((Map) value);
            } else {
                return true;
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<String> validate(StringReader input) {
        CustomProblemHandler handler = new CustomProblemHandler();
        try (JsonReader reader = service.createReader(input, schema, handler)) {
            reader.readValue();
            return handler.getProblemMessages();
        }
    }

    public String withDefaultValues(String data) {
        ValidationConfig config = service.createValidationConfig();
        config.withSchema(schema).withDefaultValues(true);
        JsonReaderFactory readerFactory = service.createReaderFactory(config.getAsMap());
        JsonReader reader = readerFactory.createReader(new StringReader(data));
        return reader.readValue().toString();
    }

    private Map<String, Object> getExternalProps(Map<String, Object> input) {
        Map<String, Object> externalData = new HashMap<>();
        if (config != null && config.hasPath("external.properties")) {
            Set<String> extProps = config.getObject("external.properties").keySet();
            externalData = input.entrySet().stream().filter(f -> extProps.contains(f.getKey()) && f.getValue()!=null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            input.keySet().removeAll(extProps);
        }
        return externalData;

    }

    private Map<String, Object> getRelations(Map<String, Object> data) {
        if (this.getConfig().hasPath("relations")) {
            Set<String> relKeys = this.getConfig().getObject("relations").keySet();
            Map<String, Object> relationData = data.entrySet().stream().filter(e -> relKeys.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            for (String relKey: relKeys) {
                data.remove(relKey);
            }
            return relationData;
        } else {
            return null;
        }
    }

    /**
     * Fetch all the properties of type JSON from the definition
     * @return
     */
    public List<String> getJsonProps() {
        try {
            return ((Map<String, Object>) (new ObjectMapper().readValue(((BasicJsonSchema) schema).get("properties")
                    .getValueAsJson().asJsonObject().toString(), Map.class))).entrySet().stream().filter(entry ->
                    StringUtils.equalsIgnoreCase("object", (String) ((Map<String, Object>) entry.getValue()).get("type")) ||
                            (null!=((Map<String, Object>) entry.getValue()).get("items") && StringUtils.equalsIgnoreCase("object", (String) ((Map<String, Object>) ((Map<String, Object>) entry.getValue()).get("items")).get("type")))
            ).map(entry -> entry.getKey()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
