package org.sunbird.search.quantization;

/**
 * Int8 quantization for float embedding vectors. Mirrors
 * content-embedding-job's Int8QuantizationStrategy so query-time vectors land
 * in the same byte-space as indexed vectors.
 *
 * Two paths based on the L2 norm:
 *  - normalized (norm ≈ 1, tol 0.01): byte = round(clamp(v * 127, -127, 127))
 *  - unnormalized: per-vector min-max into [-128, 127]
 *
 * Tolerance and constants must stay identical to the embedding job. Drift
 * here corrupts query/index space alignment and recall collapses.
 */
public class Int8QuantizationStrategy implements QuantizationStrategy {

    private static final double NORM_TOLERANCE = 0.01;

    @Override
    public String getName() {
        return "int8";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public byte[] quantize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return new byte[0];
        }
        double l2 = 0.0;
        for (float v : vector) l2 += (double) v * (double) v;
        l2 = Math.sqrt(l2);

        boolean normalized = Math.abs(l2 - 1.0) < NORM_TOLERANCE;
        byte[] out = new byte[vector.length];

        if (normalized) {
            for (int i = 0; i < vector.length; i++) {
                double scaled = Math.max(-127.0, Math.min(127.0, vector[i] * 127.0));
                out[i] = (byte) Math.round(scaled);
            }
        } else {
            float min = vector[0];
            float max = vector[0];
            for (int i = 1; i < vector.length; i++) {
                if (vector[i] < min) min = vector[i];
                if (vector[i] > max) max = vector[i];
            }
            float range = max - min;
            if (range == 0f) {
                // All elements identical; zero is the safe centroid in byte space.
                return out;
            }
            for (int i = 0; i < vector.length; i++) {
                double n = ((double) (vector[i] - min)) / (double) range;   // [0, 1]
                double s = n * 255.0 - 128.0;                                // [-128, 127]
                s = Math.max(-128.0, Math.min(127.0, s));
                out[i] = (byte) Math.round(s);
            }
        }
        return out;
    }
}
