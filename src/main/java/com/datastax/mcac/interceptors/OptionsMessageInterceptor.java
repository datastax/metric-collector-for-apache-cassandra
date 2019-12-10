package com.datastax.mcac.interceptors;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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
import org.apache.cassandra.utils.Pair;

/**
 * We intercept the OPTIONS message to capture the Driver heartbeat messages.
 * The ExpiringMap is available in all C* versions so we use this to
 * cache the startup driver flags from the STARTUP message and send
 * them to insights every 5 minutes.  This lets insights track user sessions over time.
 *
 * Once the connection is closed the information will expire from the cache.
 */
public class OptionsMessageInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(OptionsMessageInterceptor.class);

    private static final Long lifetimeMs = TimeUnit.MINUTES.toMillis(5);
    static final ExpiringMap<Channel, Pair<Long, Map<String, String>>> stateCache = new ExpiringMap<>(lifetimeMs);

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

                Pair<Long, Map<String, String>> options = stateCache.get(request.connection().channel());
                if (options != null)
                {
                    Long now = System.currentTimeMillis();
                    Long lastInsightSend = options.left;

                    if (options != null && (now - lastInsightSend) > lifetimeMs)
                    {
                        client.get().report(new ClientConnectionInformation(queryState.getClientState(), options.right, true));
                        lastInsightSend = now;
                    }

                    //Put options back in cache to keep it alive
                    stateCache.put(request.connection().channel(), Pair.create(lastInsightSend, options.right));
                }
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem processing options message ", t);
        }

        return result;
    }
}
