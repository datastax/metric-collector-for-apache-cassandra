package com.datastax.mcac.interceptors;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mcac.insights.events.GCInformation;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import org.apache.cassandra.service.GCInspectorMXBean;

import org.apache.cassandra.concurrent.NamedThreadFactory;
import org.apache.cassandra.utils.Throwables;

/**
 * Used to collect statistics and perform operations based on GC notifications.
 *
 * The goal of this class to to have exactly one, very straight forward and inexpensive GC notification
 * callback and delegate all work to an executor, so the GC thread that invokes the GC notification
 * is not blocked for a long time.
 *
 */
public class GCListener extends AbstractInterceptor implements GCInspectorMXBean
{
    private static final Logger logger = LoggerFactory.getLogger(GCListener.class);

    /*
     * The field from java.nio.Bits that tracks the total number of allocated
     * bytes of direct memory requires via ByteBuffer.allocateDirect that have not been GCed.
     */
    final static Field BITS_TOTAL_CAPACITY;

    static
    {
        Field temp = null;
        try
        {
            Class<?> bitsClass = Class.forName("java.nio.Bits");
            Field f = bitsClass.getDeclaredField("totalCapacity");
            f.setAccessible(true);
            temp = f;
        }
        catch (Throwable t)
        {
            logger.debug("Error accessing field of java.nio.Bits", t);
            //Don't care, will just return the dummy value -1 if we can't get at the field in this JVM
        }
        BITS_TOTAL_CAPACITY = temp;
    }


    private final String oldGenPoolName;
    private final Executor executor;

    private final AtomicBoolean jmxRegistered = new AtomicBoolean();

    private final Map<String, GCState> gcStates = new HashMap<>();

    /**
     * Need this field to have a _single_ instance that can be passed to both
     * {@link MBeanServer#addNotificationListener(ObjectName, NotificationListener, NotificationFilter, Object)}
     * and {@link MBeanServer#removeNotificationListener(ObjectName, NotificationListener)}.
     */
    private final NotificationListener notificationListener = this::handleNotification;

    /**
     * Maintain state for {@link #getAndResetStats()}.
     */
    private final AtomicReference<State> state = new AtomicReference<>(new State());

    public GCListener()
    {
        this(Executors.newSingleThreadExecutor(new NamedThreadFactory("GCListener")),
                ManagementFactory.getGarbageCollectorMXBeans()
                        .stream()
                        .collect(Collectors.toMap(MemoryManagerMXBean::getName, GCState::new)),
                oldGenPoolName());
    }

    private GCListener(Executor executor,
            Map<String, GCState> gcStates,
            String oldGenPoolName)
    {
        this.executor = executor;
        this.oldGenPoolName = oldGenPoolName;
        this.gcStates.putAll(gcStates);
    }

    /**
     * Add this instance as a notification listener for all GC mbeans and register this instance as an MBean.
     *
     * @throws RuntimeException if anything goes wrong
     */
    public void registerMBeanAndGCNotifications()
    {
        if (!jmxRegistered.compareAndSet(false, true))
            return;

        Throwable err = null;

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans())
            err = Throwables.perform(err,
                    () -> server.addNotificationListener(garbageCollectorMXBean.getObjectName(),
                            notificationListener,
                            null,
                            null));

        if (err != null)
        {
            unregisterMBeanAndGCNotifications();
            throw new RuntimeException(err);
        }
    }

    /**
     * Add this instance as a notification listener for all GC mbeans and register this instance as an MBean.
     *
     * @throws RuntimeException if anything goes wrong
     */
    public void unregisterMBeanAndGCNotifications()
    {
        if (!jmxRegistered.compareAndSet(true, false))
            return;

        Throwable err = null;

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans())
            err = Throwables.perform(err,
                    () -> server.removeNotificationListener(garbageCollectorMXBean.getObjectName(),
                            notificationListener,
                            null,
                            null));

        if (err != null)
            throw new RuntimeException(err);
    }

    /**
     * Implementation method for {@link NotificationListener#handleNotification(Notification, Object)}, just not
     * visible as a public method.
     */
    // Keep the signature of NotificationListener#handleNotification(Notification, Object)
    protected void handleNotification(final Notification notification, final Object handback)
    {
        // This method is called from a GC thread and must return as fast as possible, not generate
        // "too much" garbage, not block, not perform expensive operations.

        String type = notification.getType();
        if (type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION))
        {
            // retrieve the garbage collection notification information
            CompositeData cd = (CompositeData) notification.getUserData();
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);

            String gcName = info.getGcName();
            GcInfo gcInfo = info.getGcInfo();

            GCState gcState = gcStates.get(gcName);

            Map<String, MemoryUsage> beforeMemoryUsage = gcInfo.getMemoryUsageBeforeGc();
            Map<String, MemoryUsage> afterMemoryUsage = gcInfo.getMemoryUsageAfterGc();
            GCInformation.GCRun gcRun = new GCInformation.GCRun(gcName,
                    getDuration(gcInfo, gcState),
                    beforeMemoryUsage,
                    afterMemoryUsage,
                    collectedBytes(beforeMemoryUsage, afterMemoryUsage),
                    promotedBytes(beforeMemoryUsage, afterMemoryUsage),
                    0L,
                    0L,
                    gcState.assumeGCIsOldGen);

            // Perform expensive work in another thread, as this method is called from a GC thread.
            executor.execute(() -> {
                try
                {
                    client.get().report(new GCInformation(gcRun));
                }
                catch (Throwable t)
                {
                    logger.info("Problem reporting GC info");
                }

                updateGlobalState(gcRun);
            });
        }
    }

    private static String oldGenPoolName()
    {
        return ManagementFactory.getMemoryPoolMXBeans()
                .stream()
                .map(MemoryPoolMXBean::getName)
                .filter(name -> name.contains("Old Gen") || name.contains("Tenured Gen"))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the duration of a GC run.
     *
     * The duration supplied in the notification info includes more than just
     * application stopped time for concurrent GCs. Try and do a better job coming up with a good stopped time
     * value by asking for and tracking cumulative time spent blocked in GC.
     */
    private static long getDuration(GcInfo gcInfo, GCState gcState)
    {
        long duration;

        if (!gcState.assumeGCIsPartiallyConcurrent)
        {
            duration = gcInfo.getDuration();
        }
        else
        {
            long previousTotal = gcState.lastGcTotalDuration;
            long total = gcState.collectionTime.getAsLong();
            gcState.lastGcTotalDuration = total;
            duration = total - previousTotal; // may be zero for a really fast collection
        }

        return duration;
    }

    private void updateGlobalState(GCInformation.GCRun gcRun)
    {
        // Update the "global GC information state" that is exposed via 'getAndResetStats()'.
        // It's very unlikely that this method is called concurrently, so this CAS is rather unnecessary. But who knows...
        while (true)
        {
            State prev = state.get();
            if (state.compareAndSet(prev, new State(gcRun.durationInMillis, gcRun.collectedBytes, prev)))
                break;
        }
    }


    /**
     * A best effort implementation that substacts the old-gen memory usage after and before, returning
     * the max of either 0 or that subtraction.
     */
    private long promotedBytes(Map<String, MemoryUsage> beforeMemoryUsage, Map<String, MemoryUsage> afterMemoryUsage)
    {
        return (oldGenPoolName != null && afterMemoryUsage.containsKey(oldGenPoolName))
               ? Math.max(0L, afterMemoryUsage.get(oldGenPoolName).getUsed() - beforeMemoryUsage.get(oldGenPoolName).getUsed())
               : 0L;
    }

    /**
     * A best effort implementation that sums the result of {@code Math.max(memoryUsageBefore-memoryUsageAfter)}}
     * for each memory pool.
     */
    private static long collectedBytes(Map<String, MemoryUsage> beforeMemoryUsage,
            Map<String, MemoryUsage> afterMemoryUsage)
    {
        long bytes = 0;
        for (Map.Entry<String, MemoryUsage> before : beforeMemoryUsage.entrySet())
        {
            MemoryUsage after = afterMemoryUsage.get(before.getKey());
            if (after != null)
                bytes += Math.max(before.getValue().getUsed() - after.getUsed(), 0L);
        }
        return bytes;
    }

    public double[] getAndResetStats()
    {
        State s = state.getAndSet(new State());
        double[] r = new double[7];
        r[0] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.startNanos);
        r[1] = s.maxRealTimeElapsed;
        r[2] = s.totalRealTimeElapsed;
        r[3] = s.sumSquaresRealTimeElapsed;
        r[4] = s.totalBytesReclaimed;
        r[5] = s.count;
        r[6] = getAllocatedDirectMemory();

        return r;
    }

    private static long getAllocatedDirectMemory()
    {
        if (BITS_TOTAL_CAPACITY == null) return -1;
        try
        {
            return BITS_TOTAL_CAPACITY.getLong(null);
        }
        catch (Throwable t)
        {
            logger.trace("Error accessing field of java.nio.Bits", t);
            //Don't care how or why we failed to get the value in this JVM. Return -1 to indicate failure
            return -1;
        }
    }

    /**
     * Maintains information for {@link #getAndResetStats()}.
     */
    private static final class State
    {
        final double maxRealTimeElapsed;
        final double totalRealTimeElapsed;
        final double sumSquaresRealTimeElapsed;
        final double totalBytesReclaimed;
        final double count;
        final long startNanos;

        State(double extraElapsed, double extraBytes, State prev)
        {
            this.totalRealTimeElapsed = prev.totalRealTimeElapsed + extraElapsed;
            this.totalBytesReclaimed = prev.totalBytesReclaimed + extraBytes;
            this.sumSquaresRealTimeElapsed = prev.sumSquaresRealTimeElapsed + (extraElapsed * extraElapsed);
            this.startNanos = prev.startNanos;
            this.count = prev.count + 1;
            this.maxRealTimeElapsed = Math.max(prev.maxRealTimeElapsed, extraElapsed);
        }

        State()
        {
            count = maxRealTimeElapsed = sumSquaresRealTimeElapsed = totalRealTimeElapsed = totalBytesReclaimed = 0;
            startNanos = System.currentTimeMillis();
        }
    }


    public static final class GCState
    {
        final LongSupplier collectionTime;
        final boolean assumeGCIsPartiallyConcurrent;
        final boolean assumeGCIsOldGen;
        long lastGcTotalDuration = 0;

        GCState(GarbageCollectorMXBean gc)
        {
            this(gc::getCollectionTime,
                    assumeGCIsPartiallyConcurrent(gc.getName()),
                    assumeGCIsOldGen(gc.getName()));
        }

        public GCState(LongSupplier collectionTime, boolean assumeGCIsPartiallyConcurrent, boolean assumeGCIsOldGen)
        {
            this.collectionTime = collectionTime;
            this.assumeGCIsPartiallyConcurrent = assumeGCIsPartiallyConcurrent;
            this.assumeGCIsOldGen = assumeGCIsOldGen;
        }

        /*
         * Assume that a GC type is at least partially concurrent and so a side channel method
         * should be used to calculate application stopped time due to the GC.
         *
         * If the GC isn't recognized then assume that is concurrent and we need to do our own calculation
         * via the the side channel.
         */
        private static boolean assumeGCIsPartiallyConcurrent(String gcName)
        {
            switch (gcName)
            {
                //First two are from the serial collector
                case "Copy":
                case "MarkSweepCompact":
                    //Parallel collector
                case "PS MarkSweep":
                case "PS Scavenge":
                case "G1 Young Generation":
                    //CMS young generation collector
                case "ParNew":
                    return false;
                case "ConcurrentMarkSweep":
                case "G1 Old Generation":
                    return true;
                default:
                    //Assume possibly concurrent if unsure
                    return true;
            }
        }

        /*
         * Assume that a GC type is an old generation collection so TransactionLogs.rescheduleFailedTasks()
         * should be invoked.
         *
         * Defaults to not invoking TransactionLogs.rescheduleFailedTasks() on unrecognized GC names
         */
        private static boolean assumeGCIsOldGen(String gcName)
        {
            switch (gcName)
            {
                case "Copy":
                case "PS Scavenge":
                case "G1 Young Generation":
                case "ParNew":
                    return false;
                case "MarkSweepCompact":
                case "PS MarkSweep":
                case "ConcurrentMarkSweep":
                case "G1 Old Generation":
                    return true;
                default:
                    return false;
            }
        }
    }
}
