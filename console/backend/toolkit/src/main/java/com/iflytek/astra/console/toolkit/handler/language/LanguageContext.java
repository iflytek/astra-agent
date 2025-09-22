package com.iflytek.astra.console.toolkit.handler.language;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Language context utility
 *
 *
 * @author clliu19
 * @since 2025-07-23
 */
public final class LanguageContext {

    /**
     * Default language when Locale cannot be obtained (can be changed to Locale.SIMPLIFIED_CHINESE or
     * Locale.ENGLISH as needed)
     */
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    private LanguageContext() {}

    /** Get current Locale, fallback to DEFAULT_LOCALE */
    public static Locale getLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return (locale != null) ? locale : DEFAULT_LOCALE;
    }

    /** Return as IETF BCP 47 standard language tag, such as "zh-CN", "en-US" */
    public static String getLangTag() {
        return getLocale().toLanguageTag();
    }

    /** Simple check if it's Chinese language family */
    public static boolean isZh() {
        return "zh".equalsIgnoreCase(getLocale().getLanguage());
    }

    /** Simple check if it's English language family */
    public static boolean isEn() {
        return "en".equalsIgnoreCase(getLocale().getLanguage());
    }

    /**
     * Execute code under given Locale, restore original Locale after execution (for temporary switching
     * scenarios)
     */
    public static void runWithLocale(Locale locale, Runnable runnable) {
        Locale prev = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(locale);
            runnable.run();
        } finally {
            // Restore previous context
            if (prev != null) {
                LocaleContextHolder.setLocale(prev);
            } else {
                LocaleContextHolder.resetLocaleContext();
            }
        }
    }
}
