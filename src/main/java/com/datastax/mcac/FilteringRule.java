package com.datastax.mcac;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilteringRule
{
    private static final Logger logger = LoggerFactory.getLogger(FilteringRule.class);
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String GLOBAL = "global";
    public static final String DATALOG_ONLY = "datalog";

    public static final FilteringRule ALLOWED_GLOBALLY = new FilteringRule(ALLOW, ".*", GLOBAL);


    @JsonProperty("policy")
    public final String policy;

    @JsonProperty("pattern")
    public final String pattern;

    @JsonProperty("scope")
    public final String scope;

    @JsonIgnore
    private Supplier<Pattern> patternRegex;
    @JsonIgnore
    public boolean isAllowRule;
    @JsonIgnore
    public boolean isGlobal;

    public FilteringRule()
    {
        this.policy = null;
        this.pattern = null;
        this.scope = null;
    }

    public FilteringRule(@JsonProperty("policy") String policy, @JsonProperty("pattern") String patternStr, @JsonProperty("scope") String scope)
    {
        this.scope = scope;
        this.policy = policy;
        this.pattern = patternStr;

        init();
    }

    public void init()
    {
        if (policy == null || scope == null || pattern == null)
            throw new IllegalArgumentException("Filtering rule not properly initialized");

        if (!(policy.equalsIgnoreCase(ALLOW) || policy.equalsIgnoreCase(DENY)))
            throw new IllegalArgumentException(String.format("Policy must be '%s' or '%s'", ALLOW, DENY));

        if (!(scope.equalsIgnoreCase(GLOBAL) || scope.equalsIgnoreCase(DATALOG_ONLY)))
            throw new IllegalArgumentException(String.format("Scope must be '%s' or '%s'", GLOBAL, DATALOG_ONLY));

        this.isGlobal = scope.equalsIgnoreCase(GLOBAL);
        this.isAllowRule = policy.equalsIgnoreCase(ALLOW);

        this.patternRegex = Suppliers.memoize(() ->
        {
            try
            {
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            }
            catch (PatternSyntaxException e)
            {
                throw new IllegalArgumentException("Invalid pattern: " + pattern, e);
            }
        });
    }

    public boolean matches(String name)
    {
        return patternRegex.get().matcher(name).find();
    }

    public static class FilteringRuleMatch{
        public final int index;
        public final FilteringRule rule;
        public FilteringRuleMatch(int index,FilteringRule rule){
            this.index = index;
            this.rule = rule;
        }
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FilteringRuleMatch ruleMatch = (FilteringRuleMatch) o;
            return Objects.equals(index, ruleMatch.index) &&
                Objects.equals(rule, ruleMatch.rule);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(index,rule);
        }
        @Override
        public String toString()
        {
            return "FilteringRuleMatch{" +
                "index='" + index + '\'' +
                ", rule='" + rule.toString() +
                '}';
        }
    }

    /**
     * Returns the most applicable filtering rule for this name.
     *
     * The last rule in the list of applicable rules wins.
     * @param name
     * @return The rule that applied for this name.
     */
    public static FilteringRuleMatch applyFilters(String name, List<FilteringRule> rules)
    {
        OptionalInt lastRule = IntStream.range(0, rules.size()).filter(index -> rules.get(index).matches(name)).reduce((first, second) -> second);

        if (!lastRule.isPresent())
            return new FilteringRuleMatch(0,FilteringRule.ALLOWED_GLOBALLY);

        int index = lastRule.getAsInt();
        FilteringRule rule = rules.get(index);
        if (logger.isTraceEnabled()) {
            logger.trace("Applying rule {} for Metric {}", rule,name);
        }
        return new FilteringRuleMatch(index,rule);
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilteringRule rule = (FilteringRule) o;
        return Objects.equals(policy, rule.policy) &&
                Objects.equals(pattern, rule.pattern) &&
                Objects.equals(scope, rule.scope);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(policy, pattern, scope);
    }


    @Override
    public String toString()
    {
        return "FilteringRule{" +
                "policy='" + policy + '\'' +
                ", pattern='" + pattern + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
