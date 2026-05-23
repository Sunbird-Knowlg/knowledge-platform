package org.sunbird.search.quantization;

import org.sunbird.common.exception.ClientException;

/**
 * Returns a {@link QuantizationStrategy} for the configured name. Only int8 is
 * supported in v1.
 */
public final class QuantizationStrategyFactory {

    private static final QuantizationStrategy INT8 = new Int8QuantizationStrategy();

    private QuantizationStrategyFactory() { }

    public static QuantizationStrategy get(String name) {
        if (name == null || name.isEmpty() || "int8".equalsIgnoreCase(name)) {
            return INT8;
        }
        throw new ClientException("ERR_UNSUPPORTED_QUANTIZATION",
                "Unsupported quantization strategy: " + name);
    }
}
