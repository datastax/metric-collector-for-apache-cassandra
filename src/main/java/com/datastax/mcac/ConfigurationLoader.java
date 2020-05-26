package com.datastax.mcac; 

import java.io.IOError; 
import java.io.IOException; 
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.beans.IntrospectionException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.MissingProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class ConfigurationLoader
{
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);

    /**
     * Inspect the classpath to find storage configuration file
     */
    private static URL getStorageConfigURL()
    {
        String configUrl = System.getProperty("ds-metric-collector.config");
        if (configUrl == null)
        {
            try
            {
                configUrl = "file:" + File.separator + File.separator + Paths.get(ConfigurationLoader.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParent().getParent().toAbsolutePath().toString() + "/config/metric-collector.yaml";
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException("Cannot locate " + configUrl + ".");
            }
        }

        URL url;
        try
        {
            url = new URL(configUrl);
            url.openStream().close(); // catches well-formed but bogus URLs
        }
        catch (Exception er)
        {
            ClassLoader loader = Configuration.class.getClassLoader();
            url = loader.getResource(configUrl);
            if (url == null)
            {
                throw new RuntimeException("Cannot locate " + configUrl + ".");
            }
        }

        logger.info("Configuration location: {}", url);

        return url;
    }

    private static URL storageConfigURL;

    public static Configuration loadConfig()
    {
        if (storageConfigURL == null)
            storageConfigURL = getStorageConfigURL();

        return loadConfig(storageConfigURL);
    }

    public static Configuration loadConfig(URL url)
    {
        try
        {
            logger.debug("Loading settings from {}", url);
            byte[] configBytes;
            try (InputStream is = url.openStream())
            {
                configBytes = ByteStreams.toByteArray(is);
            }
            catch (IOException e)
            {
                // getStorageConfigURL should have ruled this out
                throw new AssertionError(e);
            }

            Constructor constructor = new CustomConstructor(Configuration.class);
            PropertiesChecker propertiesChecker = new PropertiesChecker();
            constructor.setPropertyUtils(propertiesChecker);
            Yaml yaml = new Yaml(constructor);
            Configuration result = loadConfig(yaml, configBytes);
            propertiesChecker.check();

            for (FilteringRule rule : result.filtering_rules)
            {
                rule.init();
            }

            return result;
        }
        catch (YAMLException e)
        {
            throw new RuntimeException(e);
        }
    }

    static class CustomConstructor extends Constructor
    {
        CustomConstructor(Class<?> theRoot)
        {
            super(theRoot);
        }

        @Override
        protected List<Object> createDefaultList(int initSize)
        {
            return Lists.newCopyOnWriteArrayList();
        }

        @Override
        protected Map<Object, Object> createDefaultMap()
        {
            return Maps.newConcurrentMap();
        }

        @Override
        protected Set<Object> createDefaultSet(int initSize)
        {
            return Sets.newConcurrentHashSet();
        }

        @Override
        protected Set<Object> createDefaultSet()
        {
            return Sets.newConcurrentHashSet();
        }
    }

    private static Configuration loadConfig(Yaml yaml, byte[] configBytes)
    {
        Configuration config = yaml.loadAs(new ByteArrayInputStream(configBytes), Configuration.class);
        // If the configuration file is empty yaml will return null. In this case we should use the default
        // configuration to avoid hitting a NPE at a later stage.
        return config == null ? new Configuration() : config;
    }

    /**
     * Utility class to check that there are no extra properties and that properties that are not null by default
     * are not set to null.
     */
    private static class PropertiesChecker extends PropertyUtils
    {
        private final Set<String> missingProperties = new HashSet<>();

        private final Set<String> nullProperties = new HashSet<>();

        public PropertiesChecker()
        {
            setSkipMissingProperties(true);
        }

        @Override
        public Property getProperty(Class<? extends Object> type, String name) throws IntrospectionException
        {
            final Property result = super.getProperty(type, name);

            if (result instanceof MissingProperty)
            {
                missingProperties.add(result.getName());
            }

            return new Property(result.getName(), result.getType())
            {
                @Override
                public void set(Object object, Object value) throws Exception
                {
                    if (value == null && get(object) != null)
                    {
                        nullProperties.add(getName());
                    }
                    result.set(object, value);
                }

                @Override
                public Class<?>[] getActualTypeArguments()
                {
                    return result.getActualTypeArguments();
                }

                @Override
                public Object get(Object object)
                {
                    return result.get(object);
                }
            };
        }

        public void check()
        {
            if (!nullProperties.isEmpty())
            {
                throw new RuntimeException("Invalid yaml. Those properties " + nullProperties + " are not valid");
            }

            if (!missingProperties.isEmpty())
            {
                throw new RuntimeException("Invalid yaml. Please remove properties " + missingProperties + " from your yaml");
            }
        }
    }
}
