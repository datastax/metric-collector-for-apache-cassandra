package com.datastax.mcac;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class FilteringRule
{
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String GLOBAL = "global";
    public static final String DATALOG_ONLY = "datalog";

    public static final FilteringRule FILTERED_GLOBALLY = new FilteringRule(DENY, ".*", GLOBAL);
    public static final FilteringRule FILTERED_INSIGHTS = new FilteringRule(DENY, ".*", DATALOG_ONLY);
    public static final FilteringRule ALLOWED_GLOBALLY = new FilteringRule(ALLOW, ".*", GLOBAL);
    public static final FilteringRule ALLOWED_INSIGHTS = new FilteringRule(ALLOW, ".*", DATALOG_ONLY);

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

    public boolean isAllowed(String name)
    {
        boolean match = patternRegex.get().matcher(name).find();
        return isAllowRule ? match : !match;
    }

    /**
     * Returns the most applicable filtering rule for this name
     * taking into account global vs insights level scope.
     *
     * global taking precedent over insights and deny rules taking precedent over allow rules
     * @param name
     * @return The rule that applied for this name.
     */
    public static FilteringRule applyFilters(String name, Collection<FilteringRule> rules)
    {
        FilteringRule allowRule = null;
        FilteringRule denyRule = null;
        boolean hasDenyRule = false;
        boolean hasAllowRule = false;

        for (FilteringRule rule : rules)
        {
            if (rule.isAllowRule)
            {
                hasAllowRule = true;
                if ((allowRule == null || !allowRule.isGlobal) && rule.isAllowed(name))
                {
                    allowRule = rule;
                }
            }
            else
            {
                hasDenyRule = true;
                if ((denyRule == null || !denyRule.isGlobal) && !rule.isAllowed(name))
                {
                    denyRule = rule;
                }
            }
        }

        if (rules.isEmpty())
            return FilteringRule.ALLOWED_GLOBALLY;

        if (denyRule != null)
            return denyRule;

        if (allowRule != null)
            return allowRule;

        if (hasDenyRule && !hasAllowRule)
            return FilteringRule.ALLOWED_GLOBALLY;

        return FilteringRule.FILTERED_GLOBALLY;
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
