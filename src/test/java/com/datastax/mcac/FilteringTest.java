package com.datastax.mcac;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;

public class FilteringTest
{
    @Test
    public void testConfig() throws URISyntaxException, MalformedURLException
    {
        Configuration c = ConfigurationLoader.loadConfig(Resources.getResource("metric-collector.yaml").toURI().toURL());

        Assert.assertEquals(1, c.filtering_rules.size());
    }
}
