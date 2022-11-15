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
        Assert.assertEquals(new FilteringRule.FilteringRuleMatch(1,secondRule), FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1", Arrays.asList(firstRule, secondRule)));
    }

    @Test
    public void testAllowFirstLooses() {
        // Check that deny takes precedence over allow if set last
        FilteringRule firstRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric1", FilteringRule.GLOBAL);
        FilteringRule secondRule = new FilteringRule("deny", "org.apache.cassandra.metrics", FilteringRule.GLOBAL);
        Assert.assertEquals(new FilteringRule.FilteringRuleMatch(1,secondRule), FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1", Arrays.asList(firstRule, secondRule)));
    }

    @Test
    public void testMixedConditions() {
        FilteringRule unaffectedByDenyRule = new FilteringRule("allow", "org.apache.cassandra.whatever.metric3", FilteringRule.GLOBAL);
        FilteringRule firstRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric1", FilteringRule.GLOBAL);
        FilteringRule secondRule = new FilteringRule("deny", "org.apache.cassandra.metrics", FilteringRule.GLOBAL);
        FilteringRule thirdRule = new FilteringRule("allow", "org.apache.cassandra.metrics.metric2", FilteringRule.GLOBAL);
        Assert.assertEquals(new FilteringRule.FilteringRuleMatch(2,secondRule), FilteringRule.applyFilters("org.apache.cassandra.metrics.metric1",Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)));
        Assert.assertEquals(new FilteringRule.FilteringRuleMatch(3,thirdRule), FilteringRule.applyFilters("org.apache.cassandra.metrics.metric2", Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)));
        Assert.assertEquals(new FilteringRule.FilteringRuleMatch(2,secondRule), FilteringRule.applyFilters("org.apache.cassandra.metrics.metric3", Arrays.asList(unaffectedByDenyRule, firstRule, secondRule, thirdRule)));
    }
}
