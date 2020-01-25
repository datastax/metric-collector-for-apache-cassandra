package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;

import com.datastax.mcac.Agent;
import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.utils.JacksonUtil;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.transport.messages.ResultMessage;

public class QueryHandlerInterceptor extends AbstractInterceptor
{
    static final String prefix = "CALL InsightsRpc.reportInsight(";

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.isSubTypeOf(QueryHandler.class);
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("process")).intercept(MethodDelegation.to(QueryHandlerInterceptor.class));
            }
        };
    }


    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<ResultMessage> zuper) throws Throwable {
        if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof String)
        {
            String query = (String) allArguments[0];

            int jsonStart = query.indexOf(prefix);
            if (jsonStart >= 0)
            {
                int jsonEnd = query.lastIndexOf(")");
                if (jsonEnd > jsonStart)
                {
                    String json = query.substring(jsonStart + prefix.length() + 1, jsonEnd - 1);
                    if (sendInsight(json))
                    {
                        return new ResultMessage.Void();
                    }
                }
            }
        }

        return zuper.call();
    }

    static boolean sendInsight(String json)
    {
        Insight insight;
        try
        {
            insight = JacksonUtil.getObjectMapper().readValue(
                    json,
                    Insight.class);

            return client.get().report(insight);
        }
        catch (Exception e)
        {
            String errorMessage = String.format("Error converting JSON to a valid Insight.  JSON received: %s, Error: ", json);
            LoggerFactory.getLogger(Agent.class).warn(errorMessage, e);
        }

        return false;
    }
}