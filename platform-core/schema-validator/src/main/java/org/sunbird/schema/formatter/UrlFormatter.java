package org.sunbird.schema.formatter;

import org.leadpony.justify.api.InstanceType;
import org.leadpony.justify.spi.FormatAttribute;

import javax.json.JsonString;
import javax.json.JsonValue;
import java.net.URL;

public class UrlFormatter implements FormatAttribute {

    @Override
    public String name() {
        return "url";
    }

    @Override
    public InstanceType valueType() {
        return InstanceType.STRING;
    }

    @Override
    public boolean test(JsonValue value) {
        System.out.println("Validating Url...");
        String str = ((JsonString) value).getString();
        try {
            //TODO: Change it to Head Call.
            new URL(str).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
