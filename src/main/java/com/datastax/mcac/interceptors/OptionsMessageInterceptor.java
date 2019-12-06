package com.datastax.mcac.interceptors;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.ClientConnectionInformation;
import io.netty.channel.Channel;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.Message;
import org.apache.cassandra.transport.messages.OptionsMessage;
import org.apache.cassandra.utils.ExpiringMap;

public class OptionsMessageInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(OptionsMessageInterceptor.class);


    static final ExpiringMap<Channel, Map<String, String>> stateCache = new ExpiringMap<>(TimeUnit.MINUTES.toMillis(5));

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".OptionsMessage");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("execute")).intercept(MethodDelegation.to(OptionsMessageInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@This Object instance, @AllArguments Object[] allArguments, @SuperCall Callable<Message.Response> zuper) throws Throwable
    {
        Message.Response result = zuper.call();

        try
        {
            if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof QueryState)
            {
                OptionsMessage request = (OptionsMessage) instance;
                QueryState queryState = (QueryState) allArguments[0];

                Map<String, String> options = stateCache.get(request.connection().channel());
                if (options != null)
                    client.get().report(new ClientConnectionInformation(queryState.getClientState(), options, true));

                //Put options back in cache to keep it alive
                stateCache.put(request.connection().channel(), options);
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem processing options message ", t);
        }

        return result;
    }
}
