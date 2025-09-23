package com.iflytek.astra.console.toolkit.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Converts workflow input/output schema descriptions to: 1) Value templates (with default value
 * placeholders) or 2) Type templates (with stringified type placeholders only)
 *
 * <p>
 * Supported types: - Basic: string / integer / number / boolean / object - Array shorthand:
 * array-string / array-integer / array-number / array-boolean / array-object
 *
 * <p>
 * Conventions/maintaining consistency with legacy version: - boolean default value is true (can
 * adjust DEFAULT_BOOL if false is needed) - number/integer default value is 0 - string default
 * value is "" (empty string) - object default value is {} or nested template - If allowedFileType
 * exists, prioritize the first type as value/type template
 */
public class JsonConverter {

    private static final boolean DEFAULT_BOOL = true;

    // ======================== External API (keeping signatures unchanged) ========================

    /** Input: Generate "value template". */
    public static JSONObject flowInputTemplateConvert(String text) {
        return convertTopLevel(text, Mode.VALUE);
    }

    /** Input: Generate "type template". */
    public static JSONObject flowInputTypeConvert(String text) {
        return convertTopLevel(text, Mode.TYPE);
    }

    /** Output: Generate "value template". */
    public static JSONObject flowOutputTemplateConvert(String text) {
        return convertTopLevel(text, Mode.VALUE);
    }

    /** Compatible with old method name: object inner layer (value template). */
    public static JSONObject convertInnerLevel(JSONArray input) {
        return convertObjectProperties(input, Mode.VALUE);
    }

    /**
     * Compatible with old method name: object inner layer (type template), retain signature and
     * delegate to standardized method.
     */
    private static JSONObject convertInnerLeve4Type(JSONArray input) {
        return convertInnerLevelForType(input);
    }

    /** Standardized naming: object inner layer (type template). */
    private static JSONObject convertInnerLevelForType(JSONArray input) {
        return convertObjectProperties(input, Mode.TYPE);
    }

    // ======================== Core Implementation ========================

    /** Mode: Generate value template or type template. */
    private enum Mode {
        VALUE, TYPE
    }

    /**
     * Process top-level array (each item like {"name": "...", "schema": {...}}).
     */
    private static JSONObject convertTopLevel(String text, Mode mode) {
        JSONObject template = new JSONObject();
        if (StringUtils.isBlank(text)) {
            return template;
        }

        final JSONArray arr;
        try {
            arr = JSON.parseArray(text);
        } catch (Exception parseEx) {
            // Parse failure fallback to empty object
            return template;
        }
        if (arr == null || arr.isEmpty()) {
            return template;
        }

        for (int i = 0; i < arr.size(); i++) {
            JSONObject item = arr.getJSONObject(i);
            if (item == null)
                continue;

            String name = item.getString("name");
            if (StringUtils.isBlank(name))
                continue;

            // If allowedFileType exists, prioritize using it (both modes effective)
            JSONArray allowedFileType = item.getJSONArray("allowedFileType");
            if (CollectionUtils.isNotEmpty(allowedFileType)) {
                List<String> list = allowedFileType.toJavaList(String.class);
                if (!list.isEmpty()) {
                    Object value = (mode == Mode.VALUE) ? list.get(0) : list.get(0);
                    template.put(name, value);
                    continue;
                }
            }

            JSONObject schema = item.getJSONObject("schema");
            template.put(name, buildBySchema(schema, mode));
        }
        return template;
    }

    /**
     * Process object properties array (each item like {"name":"a","type":"string", ...})
     */
    private static JSONObject convertObjectProperties(JSONArray properties, Mode mode) {
        JSONObject template = new JSONObject();
        if (properties == null || properties.isEmpty()) {
            return template;
        }
        for (int i = 0; i < properties.size(); i++) {
            JSONObject prop = properties.getJSONObject(i);
            if (prop == null)
                continue;

            String name = prop.getString("name");
            if (StringUtils.isBlank(name))
                continue;

            // Object inner layer also supports allowedFileType
            JSONArray allowedFileType = prop.getJSONArray("allowedFileType");
            if (CollectionUtils.isNotEmpty(allowedFileType)) {
                List<String> list = allowedFileType.toJavaList(String.class);
                if (!list.isEmpty()) {
                    Object value = (mode == Mode.VALUE) ? list.get(0) : list.get(0);
                    template.put(name, value);
                    continue;
                }
            }

            String type = prop.getString("type");
            template.put(name, buildByTypeAndProps(type, prop.getJSONArray("properties"), mode));
        }
        return template;
    }

    /**
     * Build value/type template based on schema. Top-level schema structure: { "type":
     * "string|object|array-xxx|...", "properties": [...] }
     */
    private static Object buildBySchema(JSONObject schema, Mode mode) {
        if (schema == null) {
            return (mode == Mode.VALUE) ? new JSONObject() : "object";
        }
        String type = schema.getString("type");
        JSONArray props = schema.getJSONArray("properties");
        return buildByTypeAndProps(type, props, mode);
    }

    /**
     * Unified type dispatch (including array-xxx shorthand and object nesting).
     */
    private static Object buildByTypeAndProps(String rawType, JSONArray propsIfObject, Mode mode) {
        final String type = StringUtils.defaultIfBlank(rawType, "object");

        // Array shorthand type: array-xxx
        if (type.startsWith("array-")) {
            String elemType = type.substring(6);
            JSONArray array = new JSONArray();
            array.add(buildArrayElement(elemType, propsIfObject, mode));
            return array;
        }

        // Basic/object types
        return switch (type) {
            case "string" -> (mode == Mode.VALUE) ? "" : "string";
            case "integer" -> (mode == Mode.VALUE) ? 0 : "integer";
            case "number" -> (mode == Mode.VALUE) ? 0 : "number";
            case "boolean" -> (mode == Mode.VALUE) ? DEFAULT_BOOL : "boolean";
            case "object" -> (mode == Mode.VALUE)
                    ? convertObjectProperties(propsIfObject, Mode.VALUE)
                    : convertObjectProperties(propsIfObject, Mode.TYPE);
            default -> {
                // Unknown type: handle conservatively
                // Value template → empty object; Type template → return type text as-is
                yield (mode == Mode.VALUE) ? new JSONObject() : type;
            }
        };
    }

    /** Array element placeholder generation (allows object elements with properties). */
    private static Object buildArrayElement(String elemType, JSONArray propsIfObject, Mode mode) {
        if (StringUtils.isBlank(elemType)) {
            return (mode == Mode.VALUE) ? new JSONObject() : "object";
        }
        return switch (elemType) {
            case "string" -> (mode == Mode.VALUE) ? "" : "string";
            case "integer" -> (mode == Mode.VALUE) ? 0 : "integer";
            case "number" -> (mode == Mode.VALUE) ? 0 : "number";
            case "boolean" -> (mode == Mode.VALUE) ? DEFAULT_BOOL : "boolean";
            case "object" -> (mode == Mode.VALUE)
                    ? convertObjectProperties(propsIfObject, Mode.VALUE)
                    : convertObjectProperties(propsIfObject, Mode.TYPE);
            default -> (mode == Mode.VALUE) ? new JSONObject() : elemType;
        };
    }
}
