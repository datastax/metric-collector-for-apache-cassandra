package com.datastax.mcac.interceptors;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.ClientConnectionInformation;
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
import org.apache.cassandra.transport.messages.StartupMessage;
import org.apache.cassandra.utils.Pair;

public class StartupMessageInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(StartupMessageInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".StartupMessage");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("execute")).intercept(MethodDelegation.to(StartupMessageInterceptor.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@This Object instance, @AllArguments Object[] allArguments, @SuperCall Callable<Message.Response> zuper) throws Throwable {
        Message.Response result = zuper.call();

        try
        {
            if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof QueryState)
            {
                QueryState queryState = (QueryState) allArguments[0];
                StartupMessage request = ((StartupMessage)instance);
                client.get().report(new ClientConnectionInformation(queryState.getClientState(), request.options, false));

                //We want to keep sending connection event information events for the duration of the session.
                //The drivers uses OPTIONS messages as a heartbeat so we
                //register the option information to be used by the OPTIONS interceptor
                OptionsMessageInterceptor.stateCache.put(request.connection().channel(), Pair.create(1L, request.options));
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem processing startup message ", t);
        }

        return result;
    }
}
