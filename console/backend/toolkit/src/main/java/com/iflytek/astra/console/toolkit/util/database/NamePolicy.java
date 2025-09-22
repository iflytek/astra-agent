package com.iflytek.astra.console.toolkit.util.database;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for generating standardized names for database objects.
 */
public final class NamePolicy {

    private NamePolicy() {}

    /**
     * Generate a copy table name by appending "_copy" and replacing any invalid characters with an
     * underscore.
     *
     * <p>
     * Rules:
     * </p>
     * <ul>
     * <li>Trim leading and trailing whitespace from the original name.</li>
     * <li>Keep only letters, digits, and underscores; replace all other characters with
     * underscores.</li>
     * <li>If the result does not already end with "_copy", append "_copy".</li>
     * </ul>
     *
     * @param origin the original table name (may be null or blank)
     * @return a normalized copy name ending with "_copy"
     */
    public static String copyName(String origin) {
        String base = StringUtils.trimToEmpty(origin);
        // Only retain letters, digits, and underscores; replace others with underscores
        String norm = base.replaceAll("[^A-Za-z0-9_]", "_");
        if (!norm.endsWith("_copy")) {
            norm = norm + "_copy";
        }
        return norm;
    }
}
