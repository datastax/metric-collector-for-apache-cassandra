package com.datastax.mcac.interceptors;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.compaction.AbstractCompactionTask;
import org.apache.cassandra.db.compaction.CompactionController;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.cassandra.db.lifecycle.LifecycleTransaction;
import org.apache.cassandra.io.sstable.ISSTableScanner;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.utils.FBUtilities;

public class LegacyCompactionStartInterceptor extends AbstractInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(LegacyCompactionStartInterceptor.class);

    private static final Field sstableListField = FBUtilities.getProtectedField(CompactionController.class, "compacting");
    static {
        sstableListField.setAccessible(true);
    }

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".CompactionIterable");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.constructor(ElementMatchers.any()).intercept(MethodDelegation.to(LegacyCompactionStartInterceptor.class).andThen(SuperMethodCall.INSTANCE));
            }
        };
    }

    public static void construct(@AllArguments Object[] allArguments) throws Throwable
    {
        //                              OperationType type,
        //                              List<ISSTableScanner> scanners,
        //                              CompactionController controller,
        //                              SSTableFormat.Type formatType,
        //                              UUID compactionId
        if (allArguments.length == 5)
        {
            try
            {
                OperationType operationType = (OperationType) allArguments[0];
                CompactionController controller = (CompactionController) allArguments[2];
                UUID id = (UUID) allArguments[4];

                String keyspace = controller.cfs.keyspace.getName();
                String table = controller.cfs.name;

                Iterable<SSTableReader> sstables = (Iterable<SSTableReader>)sstableListField.get(controller);

                List<SSTableCompactionInformation> sstableInfo = new ArrayList<>();
                long totalBytes = 0L;
                long totalOnDiskBytes = 0L;
                for (SSTableReader sstable : sstables)
                {
                    totalBytes += sstable.uncompressedLength();
                    totalOnDiskBytes += sstable.onDiskLength();

                    SSTableCompactionInformation info = new SSTableCompactionInformation(
                            sstable.getFilename(),
                            sstable.getSSTableLevel(),
                            sstable.estimatedKeys(),
                            sstable.descriptor.generation,
                            sstable.descriptor.version.getVersion(),
                            sstable.onDiskLength(),
                            controller.cfs.getCompactionParameters().get("class")
                    );

                    sstableInfo.add(info);
                }

                client.get().report(new CompactionStartedInformation(id, keyspace, table, operationType, totalBytes,
                        false, totalOnDiskBytes, sstableInfo ));

            }
            catch (Throwable t)
            {
                logger.info("Problem collection compaction start report", t);
            }
        }
    }
}
