package com.burndown.util;

import com.pgvector.PGvector;

/**
 * Utility class for working with PGvector
 */
public class PGvectorUtil {

    /**
     * Get the dimension of a PGvector
     * @param vector The PGvector instance
     * @return The dimension (length of the vector)
     */
    public static int getDimension(PGvector vector) {
        if (vector == null) {
            return 0;
        }
        // PGvector stores data as float array internally
        // The toString() format is "[1.0, 2.0, 3.0, ...]"
        String vectorStr = vector.toString();
        if (vectorStr.startsWith("[") && vectorStr.endsWith("]")) {
            String content = vectorStr.substring(1, vectorStr.length() - 1).trim();
            if (content.isEmpty()) {
                return 0;
            }
            return content.split(",").length;
        }
        return 0;
    }

    /**
     * Convert PGvector to float array
     * @param vector The PGvector instance
     * @return float array representation
     */
    public static float[] toFloatArray(PGvector vector) {
        if (vector == null) {
            return new float[0];
        }
        String vectorStr = vector.toString();
        if (vectorStr.startsWith("[") && vectorStr.endsWith("]")) {
            String content = vectorStr.substring(1, vectorStr.length() - 1).trim();
            if (content.isEmpty()) {
                return new float[0];
            }
            String[] parts = content.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        }
        return new float[0];
    }

    /**
     * Convert PGvector to PostgreSQL array string format
     * @param vector The PGvector instance
     * @return String in format "[1.0,2.0,3.0,...]"
     */
    public static String toArrayString(PGvector vector) {
        if (vector == null) {
            return "[]";
        }
        return vector.toString();
    }
}
