package com.datastax.mcac.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.databind.util.ISO8601Utils;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.FieldPosition;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JacksonUtil
{
    public static class JacksonUtilException extends IOException
    {
        private static final long serialVersionUID = 1L;

        JacksonUtilException(Throwable t)
        {
            super(t);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(JacksonUtil.class);

    private static final ImmutableList<Module> modules = ImmutableList.of(new Jdk8Module(), new JavaTimeModule());

    private JacksonUtil()
    {
        // util class should have private constructor
    }

    private static final Supplier<ObjectMapper> MAPPER_SUPPLIER = Suppliers.memoize(() -> {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        modules.forEach(mapper::registerModule);
        return mapper;
    });


    private static final Supplier<ObjectMapper> PRETTY_MAPPER_SUPPLIER = Suppliers.memoize(() -> {
        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
        modules.forEach(mapper::registerModule);
        return mapper;
    });

    public static List<Module> getObjectMapperModules()
    {
        return modules;
    }

    /**
     * <p>Public getters/setters/properties/creators will be auto detected.</p>
     * <p><b>NOTE</b>: public properties are allowed here to avoid getter/setter boilerplate
     * if it isn't needed for really simple items so be careful if you have a setter
     * and be mindful about annotating the setter if it has any logic within otherwise
     * it might be bypassed when deserialized!!</p>
     *
     * @return
     */
    public static ObjectMapper getObjectMapper()
    {
        return MAPPER_SUPPLIER.get();
    }

    public static ObjectMapper getPrettyObjectMapper()
    {
        return PRETTY_MAPPER_SUPPLIER.get();
    }


    /**
     * Reads a string into a JsonNode.
     */
    public static JsonNode readTree(String jsonString) throws JacksonUtilException
    {
        try
        {
            return getObjectMapper().readTree(jsonString);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Reads an input stream into a JsonNode.
     */
    public static JsonNode readTree(final InputStream stream) throws JacksonUtilException
    {
        try
        {
            return getObjectMapper().readTree(stream);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Reads an input stream and creates an object of the desired type.
     */
    public static <T> T readValue(final InputStream stream, final Class<T> type) throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerFor(type);
            return reader.readValue(stream);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts a JSON string to an object of the given class.
     * <p>Not actually sure why this is different from createObjectFromJson</p>
     */
    public static <T> T readValue(final String json, final Class<T> type) throws JacksonUtilException
    {
        try
        {
            return getObjectMapper().readValue(json, type);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts a JSON string to an object of the given class.
     */
    public static <T> T createObjectFromJson(final String jsonString, final Class<T> clazz) throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerFor(clazz);
            return reader.readValue(jsonString);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts a JSON string to an object specified by a type reference.
     */
    public static <T> T createObjectFromJson(String jsonString, TypeReference<T> typeReference)
            throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerFor(typeReference);
            return reader.readValue(jsonString);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Updates a Jackson annotated type with values from the given {@link ObjectNode}.
     */
    public static <T> T updateValueFromObjectNode(final T valueToUpdate, final ObjectNode json)
            throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerForUpdating(valueToUpdate);
            return reader.readValue(json);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Updates a Jackson annotated type with values from the given JSON string.
     */
    public static <T> T updateValueFromJsonString(final T valueToUpdate, final String jsonString)
            throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerForUpdating(valueToUpdate);
            return reader.readValue(jsonString);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts an Object to an {@link ObjectNode}.
     */
    public static ObjectNode convertValueToObjectNode(final Object fromValue) throws JacksonUtilException
    {
        if (ObjectNode.class.isAssignableFrom(fromValue.getClass()))
        {
            // if the object is already an object node just return it now :P
            return (ObjectNode) fromValue;
        }

        try
        {
            return getObjectMapper().convertValue(fromValue, ObjectNode.class);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts an Object to a {@link JsonNode}.
     */
    public static JsonNode convertValueToJsonNode(final Object fromValue) throws JacksonUtilException
    {
        try
        {
            return getObjectMapper().convertValue(fromValue, JsonNode.class);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts an Object to a generic {@link Map}.
     */
    public static Map<String, Object> convertObjectToMap(final Object object) throws JacksonUtilException
    {
        try
        {
            final ObjectWriter writer = getObjectMapper().writer();
            return convertJsonToMap(writer.writeValueAsString(object));
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts a JSON string to a generic {@link Map}.
     */
    public static Map<String, Object> convertJsonToMap(final String json) throws JacksonUtilException
    {
        try
        {
            final ObjectReader reader = getObjectMapper().readerFor(new TypeReference<HashMap<String, Object>>()
            {
            });
            final Map<String, Object> map = reader.readValue(json);
            return map;
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Gets the JSON value of an Object.
     */
    public static String writeValueAsString(final Object value) throws JacksonUtilException
    {
        try
        {
            final ObjectWriter writer = getObjectMapper().writer();
            return writer.writeValueAsString(value);
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Converts a JsonNode to a given type.
     */
    public static <T> T convertJsonNodeToObject(final JsonNode node, final Class<T> clazz) throws JacksonUtilException
    {
        try
        {
            ObjectReader reader = getObjectMapper().readerFor(clazz);
            return reader.readValue(node.traverse());
        }
        catch (Exception e)
        {
            throw new JacksonUtilException(e);
        }
    }

    /**
     * Pretty print the JSON for an object.
     */
    public static String prettyPrint(Object object) throws JacksonUtilException
    {
        try
        {
            final ObjectMapper mapper = getPrettyObjectMapper();
            return mapper.writeValueAsString(object);
        }
        catch (Exception ex)
        {
            throw new JacksonUtilException(ex);
        }
    }

    /**
     * Jackson's provided {@link ISO8601DateFormat} class ignores milliseconds, so this adds them to dates without
     * using the non-thread-safe {@link java.text.SimpleDateFormat} class.
     */
    public static class Iso8601DateWithMillisFormat extends ISO8601DateFormat
    {
        @Override
        public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition)
        {
            String value = ISO8601Utils.format(date, true); // "true" to include milliseconds
            toAppendTo.append(value);
            return toAppendTo;
        }
    }
}
