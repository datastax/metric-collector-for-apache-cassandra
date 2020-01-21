package com.datastax.mcac;

import com.datastax.mcac.interceptors.CassandraDaemonInterceptor;
import com.datastax.mcac.interceptors.CompactionEndedInterceptor;
import com.datastax.mcac.interceptors.CompactionStartInterceptor;
import com.datastax.mcac.interceptors.ExceptionInterceptor;
import com.datastax.mcac.interceptors.FlushInterceptor;
import com.datastax.mcac.interceptors.FlushInterceptorLegacy;
import com.datastax.mcac.interceptors.LargePartitionInterceptor;
import com.datastax.mcac.interceptors.LegacyCompactionStartInterceptor;
import com.datastax.mcac.interceptors.LoggingInterceptor;
import com.datastax.mcac.interceptors.OptionsMessageInterceptor;
import com.datastax.mcac.interceptors.QueryHandlerInterceptor;
import com.datastax.mcac.interceptors.StartupMessageInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class Agent {

    public static void premain(String arg, Instrumentation inst) throws Exception {

        File temp = Files.createTempDirectory("tmp").toFile();
        temp.deleteOnExit();

        Map<TypeDescription, byte[]> injected = new HashMap<>();

        injected.put(new TypeDescription.ForLoadedType(LoggingInterceptor.class), ClassFileLocator.ForClassLoader.read(LoggingInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(CassandraDaemonInterceptor.class), ClassFileLocator.ForClassLoader.read(CassandraDaemonInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(QueryHandlerInterceptor.class), ClassFileLocator.ForClassLoader.read(QueryHandlerInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(StartupMessageInterceptor.class), ClassFileLocator.ForClassLoader.read(StartupMessageInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(OptionsMessageInterceptor.class), ClassFileLocator.ForClassLoader.read(OptionsMessageInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(LargePartitionInterceptor.class), ClassFileLocator.ForClassLoader.read(LargePartitionInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(FlushInterceptor.class), ClassFileLocator.ForClassLoader.read(FlushInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(FlushInterceptorLegacy.class), ClassFileLocator.ForClassLoader.read(FlushInterceptorLegacy.class));
        injected.put(new TypeDescription.ForLoadedType(ExceptionInterceptor.class), ClassFileLocator.ForClassLoader.read(ExceptionInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(CompactionStartInterceptor.class), ClassFileLocator.ForClassLoader.read(CompactionStartInterceptor.class));
        injected.put(new TypeDescription.ForLoadedType(CompactionEndedInterceptor.class), ClassFileLocator.ForClassLoader.read(CompactionEndedInterceptor.class));

        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(injected);

        new AgentBuilder.Default()
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()) //For debug
                .type(LoggingInterceptor.type())
                .transform(LoggingInterceptor.transformer())
                .installOn(inst);

        new AgentBuilder.Default()
                //.disableClassFormatChanges()
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()) //For debug
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .ignore(new AgentBuilder.RawMatcher.ForElementMatchers(nameStartsWith("net.bytebuddy.").or(isSynthetic()), any(), any()))
                .enableBootstrapInjection(inst, temp)
                //Dropped Messages
                //Exception Information
                .type(ExceptionInterceptor.type())
                .transform(ExceptionInterceptor.transformer())
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
                //Large partitions
                .type(LargePartitionInterceptor.type())
                .transform(LargePartitionInterceptor.transformer())
                //Flush Information
                .type(FlushInterceptor.type())
                .transform(FlushInterceptor.transformer())
                .type(FlushInterceptorLegacy.type())
                .transform(FlushInterceptorLegacy.transformer())
                //Compaction Info Started
                .type(CompactionStartInterceptor.type())
                .transform(CompactionStartInterceptor.transformer())
                .type(LegacyCompactionStartInterceptor.type())
                .transform(LegacyCompactionStartInterceptor.transformer())
                //Compaction Info Ended
                .type(CompactionEndedInterceptor.type())
                .transform(CompactionEndedInterceptor.transformer())
                .installOn(inst);
    }
}