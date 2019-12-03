package com.datastax.mcac;

import com.datastax.mcac.interceptors.CassandraDaemonInterceptor;
import com.datastax.mcac.interceptors.QueryHandlerInterceptor;
import com.datastax.mcac.interceptors.StartupMessageInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.transport.messages.StartupMessage;

import java.io.File;
import java.lang.instrument.Instrumentation;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Agent {

    public static void premain(String arg, Instrumentation inst) throws Exception {

        File temp = Files.createTempDirectory("tmp").toFile();
        Map<TypeDescription, byte[]> injected = new HashMap<>();

        injected.put(new TypeDescription.ForLoadedType(CassandraDaemonInterceptor.class), ClassFileLocator.ForClassLoader.read(CassandraDaemonInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(QueryHandlerInterceptor.class), ClassFileLocator.ForClassLoader.read(QueryHandlerInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(StartupMessageInterceptor.class), ClassFileLocator.ForClassLoader.read(StartupMessageInterceptor.class));

        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(injected);

        new AgentBuilder.Default()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut()) //For debug
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .enableBootstrapInjection(inst, temp)
                .type(ElementMatchers.nameEndsWith(".CassandraDaemon"))
                .transform(new AgentBuilder.Transformer()
                {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("start")).intercept(MethodDelegation.to(CassandraDaemonInterceptor.class));
                    }
                })
                .type(ElementMatchers.isSubTypeOf(QueryHandler.class))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("process")).intercept(MethodDelegation.to(QueryHandlerInterceptor.class));
                    }
                })
                .type(ElementMatchers.nameEndsWith("StartupMessage"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("execute")).intercept(MethodDelegation.to(StartupMessageInterceptor.class));
                    }
                })
                .installOn(inst);
    }
}