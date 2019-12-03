package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;

import com.datastax.mcac.Agent;
import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.utils.JacksonUtil;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.apache.cassandra.transport.messages.ResultMessage;

public class QueryHandlerInterceptor extends AbstractInterceptor
{
    static final String prefix = "CALL InsightsRpc.reportInsight(";

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<ResultMessage> zuper) throws Throwable {
        if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof String)
        {
            String query = (String) allArguments[0];
            //LoggerFactory.getLogger(Agent.class).info("Intercepted {}", query);

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