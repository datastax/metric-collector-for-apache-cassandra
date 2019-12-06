package com.datastax.mcac;

import com.datastax.mcac.interceptors.CassandraDaemonInterceptor;
import com.datastax.mcac.interceptors.OptionsMessageInterceptor;
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
        injected.put(new TypeDescription.ForLoadedType(OptionsMessageInterceptor.class), ClassFileLocator.ForClassLoader.read(OptionsMessageInterceptor.class));

        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(injected);

        new AgentBuilder.Default()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut()) //For debug
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .enableBootstrapInjection(inst, temp)
                //Cassandra Daemon
                .type(CassandraDaemonInterceptor.type())
                .transform(CassandraDaemonInterceptor.transformer())
                //Query Handler
                .type(QueryHandlerInterceptor.type())
                .transform(QueryHandlerInterceptor.transformer())
                //Startup Message
                .type(StartupMessageInterceptor.type())
                .transform(StartupMessageInterceptor.transformer())
                //Options Message
                .type(OptionsMessageInterceptor.type())
                .transform(OptionsMessageInterceptor.transformer())
                .installOn(inst);
    }
}