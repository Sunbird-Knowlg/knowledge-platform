package org.sunbird.schema.impl;

import com.typesafe.config.ConfigFactory;
import org.leadpony.justify.api.JsonSchema;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;


public class JsonSchemaValidator extends BaseSchemaValidator {

    private String basePath = "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/";

    public JsonSchemaValidator(String name, String version) throws Exception {
        super(name, version);
        basePath = basePath + name.toLowerCase() + "/" + version + "/";
        loadSchema();
        loadConfig();
    }

    private void loadSchema() throws Exception {
        System.out.println("Schema path: " + basePath + "schema.json");
        InputStream stream = new URL( basePath + "schema.json").openStream();
        this.schema = readSchema(stream);
    }

    private void loadConfig() throws Exception {
//        URI uri = getClass().getClassLoader().getResource( basePath + "config.json").toURI();
        System.out.println("Config path: " + basePath + "config.json");
        this.config = ConfigFactory.parseURL(new URL( basePath + "config.json"));

    }


    /**
     * Resolves the referenced JSON schemas.
     *
     * @param id the identifier of the referenced JSON schemas.
     * @return referenced JSON schemas.
     */
    public JsonSchema resolveSchema(URI id) {
        // The schema is available in the local filesystem.
//        try {
//            Path path = Paths.get( getClass().getClassLoader().getResource(basePath + id.getPath()).toURI());
//            return readSchema(path);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return null;
    }
}
