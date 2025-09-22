package com.iflytek.astra.console.commons.util;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internationalization utility class for message retrieval and locale operations.
 *
 * The class relies on Spring's ApplicationContext for message resolution and HTTP request context
 * for locale information.
 *
 * @author Astra Console Team
 * @since 1.0
 */
public class I18nUtil {
    private static final Logger log = LoggerFactory.getLogger(I18nUtil.class);

    private I18nUtil() {}

    /**
     * Retrieve internationalization message by key
     *
     * @param msgKey the message key to look up in the resource bundle
     * @return the localized message string corresponding to the key, or the key itself if no message is
     *         found for the current locale
     */
    public static String getMessage(String msgKey) {
        return getMessage(msgKey, null);
    }

    /**
     * Retrieve internationalization message by key with arguments for placeholder substitution
     *
     * @param msgKey the message key to look up in the resource bundle
     * @param args array of arguments to substitute into message placeholders (e.g., {0}, {1}, {2}) Can
     *        be null if no arguments are needed
     * @return the localized message string with arguments substituted into placeholders, or the key
     *         itself if no message is found for the current locale
     */
    public static String getMessage(String msgKey, String[] args) {
        try {
            Locale locale = getRequestLocale();
            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            if (applicationContext != null) {
                return applicationContext.getMessage(msgKey, args, msgKey, locale);
            }
        } catch (Exception e) {
            log.warn("Failed to get message for key: {}, falling back to key itself", msgKey, e);
        }
        return msgKey;
    }

    /**
     * Get the current user's language code from HTTP request Accept-Language header
     *
     * @return Possible language codes include but are not limited to: - "zh" (Chinese) - "en" (English)
     *         - "ja" (Japanese) - "ko" (Korean) - "fr" (French) - "de" (German) - "es" (Spanish) - "ru"
     *         (Russian) - "ar" (Arabic) and other ISO 639-1 standard two-letter lowercase language
     *         codes
     */
    public static String getLanguage() {
        return getRequestLocale().getLanguage().toLowerCase();
    }

    /**
     * Get the locale from the current HTTP request context This method retrieves the locale from the
     * Accept-Language header in the HTTP request
     *
     * @return the locale from request context, or en_US as fallback if no request context available
     */
    private static Locale getRequestLocale() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getLocale();
            }
        } catch (Exception e) {
            log.debug("Failed to get locale from request context, falling back to en_US", e);
        }
        return Locale.US;
    }
}
