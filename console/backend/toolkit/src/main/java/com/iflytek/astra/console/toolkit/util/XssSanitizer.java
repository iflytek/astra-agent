package com.iflytek.astra.console.toolkit.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Utility class for sanitizing user input to prevent XSS (Cross-Site Scripting).
 *
 * <p>
 * This class uses the OWASP Java HTML Sanitizer library with a predefined policy that allows safe
 * formatting tags and hyperlinks.
 * </p>
 *
 * <p>
 * Thread-safety: {@link PolicyFactory} is immutable and can be shared safely across threads.
 * </p>
 */
public class XssSanitizer {

    /**
     * Policy factory combining basic formatting and link sanitizers.
     */
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    /**
     * Sanitize a user-provided input string by removing or escaping potentially unsafe HTML.
     *
     * @param input the raw input string (may be {@code null})
     * @return sanitized string; never {@code null} (an empty string will be returned if input is
     *         {@code null})
     * @throws RuntimeException if the sanitizer encounters unexpected internal errors
     */
    public static String sanitize(String input) {
        return POLICY.sanitize(input);
    }
}
