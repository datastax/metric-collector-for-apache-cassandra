package com.datastax.mcac.interceptors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.CompactionStartedInformation;
import com.datastax.mcac.insights.events.SSTableCompactionInformation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.compaction.AbstractCompactionTask;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.cassandra.db.lifecycle.LifecycleTransaction;
import org.apache.cassandra.io.sstable.format.SSTableReader;

public class CompactionStartInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(FlushInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.isSubTypeOf(AbstractCompactionTask.class);
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.constructor(ElementMatchers.any()).intercept(MethodDelegation.to(CompactionStartInterceptor.class).andThen(SuperMethodCall.INSTANCE));
            }
        };
    }

    public static void construct(@AllArguments Object[] allArguments) throws Throwable
    {
        if (allArguments.length == 3)
        {
            try
            {
                ColumnFamilyStore cfs = (ColumnFamilyStore) allArguments[0];
                LifecycleTransaction txn = (LifecycleTransaction) allArguments[1];

                UUID id = txn.opId();
                String keyspace = cfs.keyspace.getName();
                String table = cfs.name;

                Collection<SSTableReader> sstables = txn.originals();
                List<SSTableCompactionInformation> sstableList = new ArrayList<>(sstables.size());
                long totalBytes = 0L;
                long totalOnDiskBytes = 0L;
                for (SSTableReader sstable : sstables)
                {

                    totalBytes += sstable.uncompressedLength();
                    totalOnDiskBytes += sstable.onDiskLength();

                    SSTableCompactionInformation info = new SSTableCompactionInformation(
                            sstable.getFilename(),
                            sstable.getSSTableLevel(),
                            sstable.getTotalRows(),
                            sstable.descriptor.generation,
                            sstable.descriptor.version.getVersion(),
                            sstable.onDiskLength(),
                            cfs.getCompactionStrategyManager().getName()
                    );

                    sstableList.add(info);
                }

                client.get().report(new CompactionStartedInformation(id, keyspace, table, OperationType.COMPACTION, totalBytes,
                        false, totalOnDiskBytes, sstableList ));

            }
            catch (Throwable t)
            {
                logger.info("Problem collection compaction start report", t);
            }
        }
    }
}
