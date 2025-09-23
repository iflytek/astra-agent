package com.iflytek.astra.console.toolkit.util.database;

import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for safely rendering SQL identifiers, literals, and values. Provides strict
 * validation to prevent SQL injection and unsafe usage.
 */
public final class SqlRenderer {

    private SqlRenderer() {}

    /**
     * Allowed identifier pattern: start with letter/underscore, followed by letters/digits/underscores
     */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** Reserved SQL keywords (can be extended as needed) */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "TRUNCATE",
            "COMMENT", "WHERE", "FROM", "TABLE", "COLUMN", "AND", "OR", "NOT", "JOIN", "ON",
            "INTO", "VALUES", "SET", "ORDER", "GROUP", "BY", "LIMIT", "OFFSET", "AS"));

    /** Safe threshold for identifier length (PostgreSQL default is 63) */
    public static final int MAX_IDENTIFIER_LENGTH = 63;

    /** Safe threshold for literal string length */
    public static final int MAX_LITERAL_LENGTH = 4096;

    /**
     * Safely quote a table/column identifier with double quotes after strict validation.
     *
     * <p>
     * Validation rules:
     * </p>
     * <ul>
     * <li>Trim whitespace.</li>
     * <li>Length must be within 1 and {@link #MAX_IDENTIFIER_LENGTH}.</li>
     * <li>Only letters, digits, and underscores are allowed.</li>
     * <li>Reserved SQL keywords are not allowed.</li>
     * <li>Double quotes inside the identifier will be escaped as "".</li>
     * </ul>
     *
     * @param name the original identifier
     * @return quoted identifier string (with double quotes)
     * @throws IllegalArgumentException if identifier is empty, too long, invalid, or reserved
     */
    public static String quoteIdent(String name) {
        String n = StringUtils.trimToEmpty(name);
        denyDangerousChars(n);
        if (n.length() == 0 || n.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Illegal identifier length: " + name);
        }
        // Allow mixed naming like "name_copy", but disallow destructive characters
        if (!IDENTIFIER.matcher(n).matches()) {
            throw new IllegalArgumentException("Illegal identifier: " + name);
        }
        String upper = n.toUpperCase(Locale.ROOT);
        if (RESERVED.contains(upper)) {
            throw new IllegalArgumentException("Identifier is reserved keyword: " + name);
        }
        // PostgreSQL/SQL safe form: wrap with double quotes; escape inner quotes
        return "\"" + n.replace("\"", "\"\"") + "\"";
    }

    /**
     * Safely escape a string literal for SQL (single quote -> two single quotes).
     *
     * @param s the input string (null will be treated as empty string)
     * @return quoted string literal
     * @throws IllegalArgumentException if literal is too long or contains control characters
     */
    public static String quoteLiteral(String s) {
        String v = (s == null) ? "" : s;
        if (v.length() > MAX_LITERAL_LENGTH) {
            throw new IllegalArgumentException("Literal too long");
        }
        denyDangerousChars(v);
        return "'" + v.replace("'", "''") + "'";
    }

    /**
     * Render an object value into SQL-safe form.
     *
     * <ul>
     * <li>String / Date / Time → quoted literal</li>
     * <li>Number / Boolean → plain output</li>
     * <li>null → NULL</li>
     * </ul>
     *
     * @param v the input value
     * @return SQL-safe string representation
     * @throws IllegalArgumentException if string rendering violates literal rules
     */
    public static String renderValue(Object v) {
        if (v == null)
            return "NULL";
        if (v instanceof Number)
            return v.toString();
        if (v instanceof Boolean)
            return ((Boolean) v) ? "TRUE" : "FALSE";
        return quoteLiteral(String.valueOf(v));
    }

    /**
     * Deny multiple SQL statements or SQL comments in input.
     *
     * <p>
     * Rules:
     * </p>
     * <ul>
     * <li>Allow a single trailing semicolon only.</li>
     * <li>Reject if more than one semicolon is found.</li>
     * <li>Reject if comments are detected ({@code --}, <code>&#47;*</code>, <code>*&#47;</code>).</li>
     * </ul>
     *
     * @param s the SQL input
     * @throws IllegalArgumentException if multiple statements or comments are found
     */
    public static void denyMultiStmtOrComment(String s) {
        if (s == null)
            return;
        String x = s.trim();

        // Allow one trailing semicolon
        if (x.endsWith(";")) {
            x = x.substring(0, x.length() - 1).trim();
        }
        // Check for other semicolons
        if (x.contains(";")) {
            throw new IllegalArgumentException("Multiple statements are not allowed");
        }
        // Check for comments
        if (x.contains("--") || x.contains("/*") || x.contains("*/")) {
            throw new IllegalArgumentException("SQL comments are not allowed");
        }
    }

    /**
     * Reject input containing control characters or newline characters to prevent hidden payloads.
     *
     * @param s the input string
     * @throws IllegalArgumentException if control characters are found
     */
    private static void denyDangerousChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c == 127) {
                throw new IllegalArgumentException("Illegal control char in input");
            }
        }
    }

    /**
     * Validate that a value is a numeric long, with whitelist check. Typically used for "WHERE id IN
     * (...)" clauses.
     *
     * @param v the input value
     * @param field the field name for error reporting
     * @return parsed long value
     * @throws IllegalArgumentException if input cannot be parsed as long
     */
    public static long requireLong(Object v, String field) {
        try {
            if (v instanceof Number)
                return ((Number) v).longValue();
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid numeric: " + field);
        }
    }
}
