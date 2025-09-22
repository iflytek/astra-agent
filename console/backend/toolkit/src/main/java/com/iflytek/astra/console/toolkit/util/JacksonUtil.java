package com.iflytek.astra.console.toolkit.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * Utility class for JSON serialization and deserialization based on Jackson.
 *
 * <p>
 * Provides common methods to parse JSON strings/files into Java objects, convert objects to JSON
 * strings/byte arrays, and manipulate {@link JsonNode} trees.
 * </p>
 *
 * <p>
 * Thread safety: ObjectMapper instances are thread-safe after configuration and can be reused
 * across threads.
 * </p>
 */
@Slf4j
public class JacksonUtil {

    public static final ObjectMapper ALWAYS_OBJECT_MAPPER = new ObjectMapper();
    public static final ObjectMapper NON_NULL_OBJECT_MAPPER = new ObjectMapper();

    /** Standard date-time format used for serialization and deserialization. */
    private static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** Initialize static ObjectMapper instances. */
    static {
        // Serialize all fields of objects
        ALWAYS_OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // Do not use timestamps for dates
        ALWAYS_OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Ignore empty bean serialization errors
        ALWAYS_OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // Set unified date format
        ALWAYS_OBJECT_MAPPER.setDateFormat(new SimpleDateFormat(STANDARD_FORMAT));
        // Ignore unknown properties during deserialization
        ALWAYS_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Serialize only non-null fields
        NON_NULL_OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        NON_NULL_OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        NON_NULL_OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        NON_NULL_OBJECT_MAPPER.setDateFormat(new SimpleDateFormat(STANDARD_FORMAT));
        NON_NULL_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // =========================== JSON to Object ===========================

    /**
     * Parse JSON string into an object of the given class.
     *
     * @param jsonString the JSON string
     * @param object target class
     * @param <T> type parameter
     * @return parsed object, or null if parsing fails
     */
    public static <T> T parseObject(String jsonString, Class<T> object) {
        T t = null;
        try {
            t = ALWAYS_OBJECT_MAPPER.readValue(jsonString, object);
        } catch (Exception e) {
            log.error("Failed to convert JSON string to object: {}", e.getMessage());
        }
        return t;
    }

    /**
     * Parse JSON file into an object of the given class.
     *
     * @param file JSON file
     * @param object target class
     * @param <T> type parameter
     * @return parsed object, or null if parsing fails
     */
    public static <T> T parseObject(File file, Class<T> object) {
        T t = null;
        try {
            t = ALWAYS_OBJECT_MAPPER.readValue(file, object);
        } catch (IOException e) {
            log.error("Failed to read JSON from file: {}", e.getMessage());
        }
        return t;
    }

    /**
     * Parse JSON array string into a List or Map.
     *
     * @param jsonArray JSON array string
     * @param reference type reference (e.g., new TypeReference&lt;List&lt;T&gt;&gt;(){})
     * @param <T> type parameter
     * @return parsed list or map, or null if parsing fails
     */
    public static <T> T parseJSONArray(String jsonArray, TypeReference<T> reference) {
        T t = null;
        try {
            t = ALWAYS_OBJECT_MAPPER.readValue(jsonArray, reference);
        } catch (Exception e) {
            log.error("Failed to convert JSONArray to List or Map: {}", e.getMessage());
        }
        return t;
    }

    // =========================== Object to JSON ===========================

    /**
     * Convert object to JSON string using the provided ObjectMapper.
     *
     * @param object object to convert
     * @param objectMapper the ObjectMapper to use
     * @return JSON string, or null if conversion fails
     */
    public static String toJSONString(Object object, ObjectMapper objectMapper) {
        String jsonString = null;
        try {
            jsonString = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert Object to JSON string: {}", e.getMessage());
        }
        return jsonString;
    }

    /**
     * Convert object to JSON string using the default mapper.
     *
     * @param object object to convert
     * @return JSON string, or null if conversion fails
     */
    public static String toJSONString(Object object) {
        return toJSONString(object, ALWAYS_OBJECT_MAPPER);
    }

    /**
     * Convert object to byte array.
     *
     * @param object object to convert
     * @return JSON byte array, or null if conversion fails
     */
    public static byte[] toByteArray(Object object) {
        byte[] bytes = null;
        try {
            bytes = ALWAYS_OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert Object to byte array: {}", e.getMessage());
        }
        return bytes;
    }

    /**
     * Write object to file as JSON.
     *
     * @param file target file
     * @param object object to write
     */
    public static void objectToFile(File file, Object object) {
        try {
            ALWAYS_OBJECT_MAPPER.writeValue(file, object);
        } catch (JsonProcessingException e) {
            log.error("Failed to write Object to file: {}", e.getMessage());
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
        }
    }

    // =========================== JsonNode related ===========================

    /**
     * Parse JSON string into a {@link JsonNode}.
     *
     * @param jsonString JSON string
     * @return JsonNode, or null if parsing fails
     */
    public static JsonNode parseJSONObject(String jsonString) {
        JsonNode jsonNode = null;
        try {
            jsonNode = ALWAYS_OBJECT_MAPPER.readTree(jsonString);
        } catch (Exception e) {
            log.error("Failed to convert JSON string to JsonNode: {}", e.getMessage());
        }
        return jsonNode;
    }

    /**
     * Convert an object into a {@link JsonNode}.
     *
     * @param object object to convert
     * @return JsonNode representation of the object
     */
    public static JsonNode parseJSONObject(Object object) {
        return ALWAYS_OBJECT_MAPPER.valueToTree(object);
    }

    /**
     * Convert a {@link JsonNode} into JSON string.
     *
     * @param jsonNode the JsonNode to convert
     * @return JSON string, or null if conversion fails
     */
    public static String toJSONString(JsonNode jsonNode) {
        String jsonString = null;
        try {
            jsonString = ALWAYS_OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JsonNode to JSON string: {}", e.getMessage());
        }
        return jsonString;
    }

    /**
     * Create a new empty {@link ObjectNode}.
     *
     * @return new ObjectNode instance
     */
    public static ObjectNode newJSONObject() {
        return ALWAYS_OBJECT_MAPPER.createObjectNode();
    }

    /**
     * Create a new empty {@link ArrayNode}.
     *
     * @return new ArrayNode instance
     */
    public static ArrayNode newJSONArray() {
        return ALWAYS_OBJECT_MAPPER.createArrayNode();
    }

    // =========================== Get values from JsonNode ===========================

    /**
     * Get a string value by key from a JsonNode object.
     *
     * @param jsonObject the JsonNode object
     * @param key the key
     * @return string value, never null
     */
    public static String getString(JsonNode jsonObject, String key) {
        return jsonObject.get(key).asText();
    }

    /**
     * Get an integer value by key from a JsonNode object.
     *
     * @param jsonObject the JsonNode object
     * @param key the key
     * @return integer value
     */
    public static Integer getInteger(JsonNode jsonObject, String key) {
        return jsonObject.get(key).asInt();
    }

    /**
     * Get a boolean value by key from a JsonNode object.
     *
     * @param jsonObject the JsonNode object
     * @param key the key
     * @return boolean value
     */
    public static Boolean getBoolean(JsonNode jsonObject, String key) {
        return jsonObject.get(key).asBoolean();
    }

    /**
     * Get a nested JsonNode by key from a JsonNode object.
     *
     * @param jsonObject the JsonNode object
     * @param key the key
     * @return nested JsonNode
     */
    public static JsonNode getJSONObject(JsonNode jsonObject, String key) {
        return jsonObject.get(key);
    }
}
