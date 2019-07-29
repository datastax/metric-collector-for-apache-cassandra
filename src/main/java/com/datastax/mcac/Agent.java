package com.datastax.mcac;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.lang.instrument.Instrumentation;

import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.Callable;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class Agent {

    private static final Supplier<UnixSocketClient> client = Suppliers.memoize(() -> new UnixSocketClient());

    public static void premain(String arg, Instrumentation inst) throws Exception {

        File temp = Files.createTempDirectory("tmp").toFile();
        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst)
                .inject(Collections.singletonMap(
                        new TypeDescription.ForLoadedType(MyInterceptor.class),
                        ClassFileLocator.ForClassLoader.read(MyInterceptor.class)));

        new AgentBuilder.Default()
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .enableBootstrapInjection(inst, temp)
                .type(ElementMatchers.nameEndsWith(".CassandraDaemon"))
                .transform(new AgentBuilder.Transformer()
                {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
                    {
                        return builder.method(ElementMatchers.named("start")).intercept(MethodDelegation.to(MyInterceptor.class));
                    }

                }).installOn(inst);
    }

    public static class MyInterceptor {

        public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
            zuper.call();

            System.err.println("Starting DataStax Metric Collector for Apache Cassandra v0.1");
            client.get().start();

            //Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                client.get().close();
            }));
        }
    }
}