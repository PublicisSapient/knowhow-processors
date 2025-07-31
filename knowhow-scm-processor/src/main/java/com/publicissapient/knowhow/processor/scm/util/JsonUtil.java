package com.publicissapient.knowhow.processor.scm.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Utility class for JSON operations.
 * 
 * This class provides helper methods for JSON serialization and deserialization
 * using Jackson ObjectMapper with proper configuration for the application.
 */
public final class JsonUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts an object to JSON string.
     * 
     * @param object the object to convert
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts an object to pretty-printed JSON string.
     * 
     * @param object the object to convert
     * @return pretty-printed JSON string
     */
    public static String toPrettyJson(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to pretty JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a JSON string to an object of the specified type.
     * 
     * @param json the JSON string
     * @param clazz the target class type
     * @param <T> the type parameter
     * @return the deserialized object, or null if conversion fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error converting JSON to object of type {}: {}", clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a JSON string to an object using TypeReference.
     * 
     * @param json the JSON string
     * @param typeReference the type reference
     * @param <T> the type parameter
     * @return the deserialized object, or null if conversion fails
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error converting JSON to object using TypeReference: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a JSON string to a Map.
     * 
     * @param json the JSON string
     * @return Map representation of the JSON, or null if conversion fails
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Converts an object to a Map.
     * 
     * @param object the object to convert
     * @return Map representation of the object, or null if conversion fails
     */
    public static Map<String, Object> toMap(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException e) {
            logger.error("Error converting object to Map: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a Map to an object of the specified type.
     * 
     * @param map the map to convert
     * @param clazz the target class type
     * @param <T> the type parameter
     * @return the converted object, or null if conversion fails
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        
        try {
            return objectMapper.convertValue(map, clazz);
        } catch (IllegalArgumentException e) {
            logger.error("Error converting Map to object of type {}: {}", clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a string is valid JSON.
     * 
     * @param json the string to validate
     * @return true if valid JSON, false otherwise
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Deep clones an object using JSON serialization/deserialization.
     * 
     * @param object the object to clone
     * @param clazz the class type
     * @param <T> the type parameter
     * @return cloned object, or null if cloning fails
     */
    public static <T> T deepClone(T object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        
        String json = toJson(object);
        return fromJson(json, clazz);
    }

    /**
     * Merges two objects by converting them to JSON and back.
     * The second object's properties will override the first object's properties.
     * 
     * @param base the base object
     * @param override the object with overriding properties
     * @param clazz the target class type
     * @param <T> the type parameter
     * @return merged object, or null if merging fails
     */
    public static <T> T merge(T base, T override, Class<T> clazz) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        
        try {
            Map<String, Object> baseMap = toMap(base);
            Map<String, Object> overrideMap = toMap(override);
            
            if (baseMap == null || overrideMap == null) {
                return null;
            }
            
            baseMap.putAll(overrideMap);
            return fromMap(baseMap, clazz);
        } catch (Exception e) {
            logger.error("Error merging objects: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the configured ObjectMapper instance.
     * 
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Extracts a specific field value from a JSON string.
     * 
     * @param json the JSON string
     * @param fieldPath the field path (e.g., "user.name" for nested fields)
     * @return the field value as string, or null if not found
     */
    public static String extractField(String json, String fieldPath) {
        if (json == null || fieldPath == null) {
            return null;
        }
        
        try {
            var jsonNode = objectMapper.readTree(json);
            String[] pathParts = fieldPath.split("\\.");
            
            var currentNode = jsonNode;
            for (String part : pathParts) {
                currentNode = currentNode.get(part);
                if (currentNode == null) {
                    return null;
                }
            }
            
            return currentNode.asText();
        } catch (JsonProcessingException e) {
            logger.error("Error extracting field '{}' from JSON: {}", fieldPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Safely converts an object to JSON with error handling.
     * Returns a default value if conversion fails.
     * 
     * @param object the object to convert
     * @param defaultValue the default value to return on error
     * @return JSON string or default value
     */
    public static String toJsonSafe(Object object, String defaultValue) {
        String result = toJson(object);
        return result != null ? result : defaultValue;
    }

    /**
     * Safely converts JSON to object with error handling.
     * Returns a default value if conversion fails.
     * 
     * @param json the JSON string
     * @param clazz the target class type
     * @param defaultValue the default value to return on error
     * @param <T> the type parameter
     * @return deserialized object or default value
     */
    public static <T> T fromJsonSafe(String json, Class<T> clazz, T defaultValue) {
        T result = fromJson(json, clazz);
        return result != null ? result : defaultValue;
    }
}