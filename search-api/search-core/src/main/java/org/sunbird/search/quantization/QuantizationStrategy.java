package org.sunbird.search.quantization;

/**
 * Mirrors the content-embedding-job's QuantizationStrategy. At query time we
 * only need the forward direction (float -> byte). Dequantize is not needed
 * because OpenSearch handles the byte-vector kNN scoring internally.
 */
public interface QuantizationStrategy {

    String getName();

    String getVersion();

    /**
     * Quantize a float vector into a byte vector suitable for an
     * OpenSearch knn_vector field with data_type=byte.
     */
    byte[] quantize(float[] vector);
}
