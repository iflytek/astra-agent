package com.iflytek.astra.console.toolkit.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collection;
import java.util.Map;

/**
 * General utility for checking "null or empty" values.
 *
 * <p>
 * Default rules:
 * </p>
 * <ul>
 * <li>{@code null} → empty</li>
 * <li>{@link CharSequence} (String/StringBuilder/StringBuffer, etc.) → {@code isBlank()}</li>
 * <li>Array ({@code Object[]}) → empty if length is 0</li>
 * <li>{@link Collection} / {@link Map} → empty if {@code isEmpty()}</li>
 * <li>{@link JSONObject} / {@link JSONArray} → empty if {@code isEmpty()} or {@code size()==0}</li>
 * <li>{@link Number} → only {@code NaN} is treated as empty; <b>0/-1 are no longer considered
 * empty</b></li>
 * <li>Other types → considered non-empty</li>
 * </ul>
 *
 * <p>
 * Compatibility: provides {@link #check(Object)} as an alias for {@link #isNullOrEmpty(Object)}.
 * </p>
 */
public final class ObjectIsNull {

    private ObjectIsNull() {}

    /**
     * Compatibility alias for {@link #isNullOrEmpty(Object)}.
     *
     * @param obj the object to check
     * @return {@code true} if null or empty, otherwise {@code false}
     */
    public static boolean check(Object obj) {
        return isNullOrEmpty(obj);
    }

    /**
     * Determine whether a single object is "null or empty".
     *
     * @param obj the object to check
     * @return {@code true} if null or empty, otherwise {@code false}
     */
    public static boolean isNullOrEmpty(Object obj) {
        if (obj == null)
            return true;
        return switch (obj) {
            case CharSequence cs -> cs.toString().trim().isEmpty() || "null".equalsIgnoreCase(cs.toString().trim());
            case Object[] arr -> arr.length == 0;
            case Collection<?> c -> c.isEmpty();
            case Map<?, ?> m -> m.isEmpty();
            // Number: only NaN is empty; 0/-1 are not considered empty by default
            case Number n -> isNumberEmpty(n);

            // All other types are considered non-empty
            default -> false;
        };
    }

    /**
     * Batch null/empty check: returns {@code true} only if all values are null or empty. Returns
     * {@code false} if at least one value is non-empty.
     *
     * <p>
     * Useful for validating that "at least one parameter is not empty" among multiple optional
     * parameters.
     * </p>
     *
     * @param objs the array of objects to check
     * @return {@code true} if all values are null or empty, otherwise {@code false}
     */
    public static boolean allNullOrEmpty(Object... objs) {
        if (objs == null || objs.length == 0)
            return true;
        for (Object o : objs) {
            if (!isNullOrEmpty(o))
                return false;
        }
        return true;
    }

    /**
     * Number-specific check.
     *
     * <p>
     * Default behavior: only NaN is treated as empty. For compatibility with legacy requirements where
     * 0/-1 are also considered empty, extend this method accordingly.
     * </p>
     *
     * @param n the number to check
     * @return {@code true} if considered empty, otherwise {@code false}
     */
    private static boolean isNumberEmpty(Number n) {
        if (n instanceof Double d) {
            if (Double.isNaN(d))
                return true;
            return false;
        }
        if (n instanceof Float f) {
            if (Float.isNaN(f))
                return true;
            return false;
        }
        if (n instanceof Integer i) {
            return false;
        }
        if (n instanceof Long l) {
            return false;
        }
        // Other Number types (Short/Byte/BigInteger/BigDecimal): not empty by default
        return false;
    }

    // ===== Precision type helper methods (optional use) =====

    /**
     * Check whether a {@link CharSequence} is blank (null, empty, or literal "null").
     *
     * @param cs the character sequence to check
     * @return {@code true} if blank, otherwise {@code false}
     */
    public static boolean isBlank(CharSequence cs) {
        return cs == null || cs.toString().trim().isEmpty() || "null".equalsIgnoreCase(cs.toString().trim());
    }

    /**
     * Check whether a {@link Collection} is empty (null or size==0).
     *
     * @param c the collection to check
     * @return {@code true} if empty, otherwise {@code false}
     */
    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /**
     * Check whether a {@link Map} is empty (null or size==0).
     *
     * @param m the map to check
     * @return {@code true} if empty, otherwise {@code false}
     */
    public static boolean isEmpty(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    /**
     * Check whether an object array is empty (null or length==0).
     *
     * @param arr the array to check
     * @return {@code true} if empty, otherwise {@code false}
     */
    public static boolean isEmpty(Object[] arr) {
        return arr == null || arr.length == 0;
    }

    /**
     * Check whether a {@link JSONObject} is empty (null or isEmpty).
     *
     * @param obj the JSONObject to check
     * @return {@code true} if empty, otherwise {@code false}
     */
    public static boolean isEmpty(JSONObject obj) {
        return obj == null || obj.isEmpty();
    }

    /**
     * Check whether a {@link JSONArray} is empty (null or size==0).
     *
     * @param arr the JSONArray to check
     * @return {@code true} if empty, otherwise {@code false}
     */
    public static boolean isEmpty(JSONArray arr) {
        return arr == null || arr.isEmpty();
    }
}
