package org.sunbird.schema.impl;

import com.typesafe.config.ConfigFactory;
import org.leadpony.justify.api.JsonSchema;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;


public class JsonSchemaValidator extends BaseSchemaValidator {

    private static String basePath = "schemas/";

    public JsonSchemaValidator(String name, String version) throws Exception {
        super(name, version);
        basePath = basePath + name.toLowerCase() + "-" + version + "/";
        loadSchema();
        loadConfig();
    }

    private void loadSchema() throws Exception {
        URI uri = getClass().getClassLoader().getResource( basePath + "schema.json").toURI();
        Path schemaPath = Paths.get(uri);
        this.schema = readSchema(schemaPath);
    }

    private void loadConfig() throws Exception {
        URI uri = getClass().getClassLoader().getResource( basePath + "config.json").toURI();
        Path configPath = Paths.get(uri);
        this.config = ConfigFactory.parseFile(configPath.toFile());

    }


    /**
     * Resolves the referenced JSON schema.
     *
     * @param id the identifier of the referenced JSON schema.
     * @return referenced JSON schema.
     */
    public JsonSchema resolveSchema(URI id) {
        // The schema is available in the local filesystem.
        try {
            Path path = Paths.get( getClass().getClassLoader().getResource(basePath + id.getPath()).toURI());
            return readSchema(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
