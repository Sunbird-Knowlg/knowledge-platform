package org.sunbird.schema;

import com.typesafe.config.Config;
import org.sunbird.schema.dto.ValidationResult;

import java.util.Map;

public interface ISchemaValidator {

    ValidationResult getStructuredData(Map<String, Object> input);

    ValidationResult validate(Map<String, Object> data) throws Exception;

    Config getConfig();
}
