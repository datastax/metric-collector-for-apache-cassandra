package com.datastax.mcac.interceptors;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class StringFormatInterceptor
{
    //public  static final Logger logger = LoggerFactory.getLogger(StringFormatInterceptor.class);
    private static final String droppedMessagesLine = "%s messages were dropped in last %d ms: %d internal and %d cross node."
            + " Mean internal dropped latency: %d ms and Mean cross-node dropped latency: %d ms";

    private static final String compactionEndLine = "Compacted (%s) %d sstables to [%s] to level=%d.  %s to %s (~%d%% of original) in %,dms.  Read Throughput = %s, Write Throughput = %s, Row Throughput = ~%,d/s.  %,d total partitions merged to %,d.  Partition merge counts were {%s}";

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.named("java.util.Formatter");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.visit(Advice.to(StringFormatInterceptor.class).on(ElementMatchers.any()));
            }
        };
    }

    @Advice.OnMethodEnter()
    public static void enter(@Advice.Origin String method) {

        System.out.println("------------------------------HERE!!!! " + method);

    }

 /*   @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<Object> zuper) throws Throwable
    {
        logger.info("HDJSHDJHSDJHSJDHJSHDJHD");

        try
        {
            if (allArguments.length > 1 && allArguments[0] instanceof String)
            {

                String fmtLine = (String) allArguments[0];

                if (fmtLine.equals(droppedMessagesLine))
                {
                    MessagingService.Verb verb = (MessagingService.Verb) allArguments[1];
                    int interval = (int) allArguments[2];
                    int droppedInternal = (int) allArguments[3];
                    int droppedCrossNode = (int) allArguments[4];
                    long internalDroppedLatencyMs = (long) allArguments[5];
                    long crossNodeDroppedLatencyMs = (long) allArguments[6];

                    client.get().report(new DroppedMessageInformation(verb, interval, droppedInternal, droppedCrossNode, internalDroppedLatencyMs, crossNodeDroppedLatencyMs));
                }
                else if (fmtLine.equals(compactionEndLine))
                {
                    UUID compactionId = (UUID) allArguments[1];
                    String[] newSSTables = ((String) allArguments[3]).split(",");
                    double postCompactionSizePercentage = ((int) allArguments[7]) / 100d;
                    double preCompactionSizePercentage = (1 - postCompactionSizePercentage) + 1;
                    long compactionTime = (long) allArguments[8];
                    long totalSourceRows = (long) allArguments[12];

                    List<SSTableCompactionInformation> sstableList = new ArrayList<>(newSSTables.length);
                    long totalBytes = 0L;
                    long totalOnDiskBytes = 0L;
                    String keyspace = null;
                    String table = null;
                    for (String newSSTable : newSSTables)
                    {
                        Descriptor d = Descriptor.fromFilename(newSSTable);
                        SSTableReader sstable = SSTableReader.open(d);
                        totalBytes += sstable.uncompressedLength();
                        totalOnDiskBytes += sstable.onDiskLength();
                        keyspace = d.ksname;
                        table = d.cfname;

                        SSTableCompactionInformation info = new SSTableCompactionInformation(
                                sstable.getFilename(),
                                sstable.getSSTableLevel(),
                                sstable.getTotalRows(),
                                sstable.descriptor.generation,
                                sstable.descriptor.version.getVersion(),
                                sstable.onDiskLength(),
                                ColumnFamilyStore.getIfExists(sstable.metadata.cfId).getCompactionStrategyManager().getName()
                        );

                        sstableList.add(info);
                    }

                    client.get().report(new CompactionEndedInformation(compactionId, keyspace, table, OperationType.COMPACTION, (long)(preCompactionSizePercentage * totalBytes), totalBytes,
                            false, totalSourceRows, totalOnDiskBytes, sstableList ));
                }
            }
        }
        catch (Throwable t)
        {
            logger.info("Problem intercepting String.format ", t);
        }

        return zuper.call();
    }*/
}
