package com.datastax.mcac.interceptors;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.ClientConnectionInformation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.Message;
import org.apache.cassandra.transport.messages.StartupMessage;

public class StartupMessageInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(StartupMessageInterceptor.class);

    @RuntimeType
    public static Object intercept(@This Object instance, @AllArguments Object[] allArguments, @SuperCall Callable<Message.Response> zuper) throws Throwable {
        Message.Response result = zuper.call();

        try
        {
            if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof QueryState)
            {
                QueryState queryState = (QueryState) allArguments[0];
                client.get().report(new ClientConnectionInformation(queryState.getClientState(), ((StartupMessage)instance).options));
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem processing startup message ", t);
        }

        return result;
    }
}
