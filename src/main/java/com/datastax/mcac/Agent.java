package com.datastax.mcac;

import com.datastax.mcac.interceptors.CassandraDaemonInterceptor;
import com.datastax.mcac.interceptors.CompactionEndedInterceptor;
import com.datastax.mcac.interceptors.CompactionStartInterceptor;
import com.datastax.mcac.interceptors.ExceptionInterceptor;
import com.datastax.mcac.interceptors.FlushInterceptor;
import com.datastax.mcac.interceptors.FlushInterceptorLegacy;
import com.datastax.mcac.interceptors.LargePartitionInterceptor;
import com.datastax.mcac.interceptors.LegacyCompactionStartInterceptor;
import com.datastax.mcac.interceptors.DroppedMessageLoggingAdvice;
import com.datastax.mcac.interceptors.OptionsMessageInterceptor;
import com.datastax.mcac.interceptors.QueryHandlerInterceptor;
import com.datastax.mcac.interceptors.StartupMessageInterceptor;
import com.datastax.mcac.interceptors.TombstoneFailureInterceptor;
import com.datastax.mcac.interceptors.TombstoneWarningInterceptor;
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

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                //For debug
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                //Dropped Messages
                .type(DroppedMessageLoggingAdvice.type())
                .transform(
                        DroppedMessageLoggingAdvice.transformer()
                ).installOn(inst);

        new AgentBuilder.Default()
                //.disableClassFormatChanges()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()) //For debug
                .ignore(new AgentBuilder.RawMatcher.ForElementMatchers(nameStartsWith("net.bytebuddy.").or(isSynthetic()), any(), any()))
                //Exception Information
                .type(ExceptionInterceptor.type())
                .transform(ExceptionInterceptor.transformer())
                //Cassandra Daemon
                .type(CassandraDaemonInterceptor.type())
                .transform(CassandraDaemonInterceptor.transformer())
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
                //Tombstone Failures
                .type(TombstoneFailureInterceptor.type())
                .transform(TombstoneFailureInterceptor.transformer())
                //Tombstone Warning
                .type(TombstoneWarningInterceptor.type())
                .transform(TombstoneWarningInterceptor.transformer())
                .installOn(inst);
    }
}