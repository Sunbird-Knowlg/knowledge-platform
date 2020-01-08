package org.sunbird.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class JsonUtils {

    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String serialize(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }

    public static <T> T deserialize(String value, Class<T> clazz) throws Exception {
        return mapper.readValue(value, clazz);
    }

    public static <T> T deserialize(InputStream value, Class<T> clazz) throws Exception {
        return mapper.readValue(value, clazz);
    }

    public static <T> T convert(Object value, Class<T> clazz) throws Exception {
        return mapper.convertValue(value, clazz);
    }

    //TODO: Rename method.
    public static Object convertJSONString(String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                Map<Object, Object> map = mapper.readValue(value, Map.class);
                return map;
            } catch (Exception e) {
                try {
                    List<Object> list = mapper.readValue(value, List.class);
                    return list;
                } catch (Exception ex) {
                    //suppress error due to invalid map while converting JSON and return null
                }
            }
        }
        return null;
    }

}
