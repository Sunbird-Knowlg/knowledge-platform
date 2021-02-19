package org.sunbird.schema.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.leadpony.justify.api.JsonSchema;
import org.sunbird.common.Platform;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;


public class JsonSchemaValidator extends BaseSchemaValidator {

    private String basePath = Platform.config.hasPath("schema.base_path") ? Platform.config.getString("schema.base_path") : "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/local/";

    public JsonSchemaValidator(String name, String version) throws Exception {
        super(name, version);
        basePath = basePath + File.separator + name.toLowerCase() + File.separator + version + File.separator;
        loadSchema();
        loadConfig();
    }

    public JsonSchemaValidator(String name, String version, String configfallback) throws Exception {
        super(name, version);
        loadSchema();
        loadConfig(name, version, configfallback);
    }

    private void loadSchema() throws Exception {
        System.out.println("Schema path: " + basePath + "schema.json");
        if(basePath.startsWith("http")){
            InputStream stream = new URL( basePath + "schema.json").openStream();
            this.schema = readSchema(stream);
        }else {
            Path schemaPath = new File( basePath + "schema.json").toPath();
            this.schema = readSchema(schemaPath);
        }
    }

    private void loadConfig() throws Exception {
        System.out.println("Config path: " + basePath + "config.json");
        if(basePath.startsWith("http")){
            this.config = ConfigFactory.parseURL(new URL( basePath + "config.json"));
        } else {
            File configFile = new File(basePath + "config.json");
            this.config = ConfigFactory.parseFile(configFile);
        }
    }

    private void loadConfig(String name, String version, String fallbackPath) throws Exception {
        if(StringUtils.isEmpty(fallbackPath))
            loadConfig();
        else {
            Config fallbackConfig = ConfigFactory.parseURL(new URL(basePath + fallbackPath.toLowerCase() + "/" + version + "/" +  "config.json"));
            basePath = basePath + name.toLowerCase() + "/" + version + "/";
            this.config = ConfigFactory.parseURL(new URL( basePath + "config.json")).withFallback(fallbackConfig);
        }
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
