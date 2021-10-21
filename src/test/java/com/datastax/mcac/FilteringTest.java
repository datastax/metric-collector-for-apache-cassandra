package com.datastax.mcac;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;

import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;

public class FilteringTest
{
    @Test
    public void testConfig() throws URISyntaxException, MalformedURLException
    {
        Configuration c = ConfigurationLoader.loadConfig(Resources.getResource("metric-collector.yaml").toURI().toURL());

        Assert.assertEquals(3, c.filtering_rules.size());
    }

    @Test
    public void testAllowLastWins() {
        // Check that allow takes precedence over deny if set last
        FilteringRule firstRule = new FilteringRule("deny", "org.apache.cassandra.metrics", FilteringRule.GLOBAL);
        FilteringRule secondRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric1", FilteringRule.GLOBAL);
        Assert.assertTrue(FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1", Arrays.asList(firstRule, secondRule)).isAllowRule);
    }

    @Test
    public void testAllowFirstLoses() {
        // Check that deny takes precedence over allow if set last
        FilteringRule firstRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric1", FilteringRule.GLOBAL);
        FilteringRule secondRule = new FilteringRule("deny", "org.apache.cassandra.metrics", FilteringRule.GLOBAL);
        Assert.assertFalse(FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1", Arrays.asList(firstRule, secondRule)).isAllowRule);
    }

    @Test
    public void testMixedConditions() {
        FilteringRule unaffectedByDenyRule = new FilteringRule("allow", "org.apache.cassandra.whatever.metric3", FilteringRule.GLOBAL);
        FilteringRule firstRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric1", FilteringRule.GLOBAL);
        FilteringRule secondRule = new FilteringRule("deny", "org.apache.cassandra.metrics", FilteringRule.GLOBAL);
        FilteringRule thirdRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric2", FilteringRule.GLOBAL);
        Assert.assertFalse(FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1", Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)).isAllowRule);
        Assert.assertTrue(FilteringRule.applyFilters("org.apache.cassandra.metrics.metric2", Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)).isAllowRule);
        Assert.assertTrue(FilteringRule.applyFilters("org.apache.cassandra.whatever.metric3", Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)).isAllowRule);
    }
}
