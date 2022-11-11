package com.datastax.mcac;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.datastax.mcac.insights.Insight;
import com.datastax.mcac.insights.metrics.RateStats;
import com.datastax.mcac.insights.metrics.SamplingStats;
import com.datastax.mcac.utils.JacksonUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.cassandra.concurrent.ScheduledExecutors;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.EstimatedHistogram;
import org.apache.cassandra.utils.NoSpamLogger;
import org.apache.cassandra.utils.Pair;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class UnixSocketClient {

    /*
     * Defaults, and system properties to allow customisation, to Netty's write buffer watermarks.
     *
     * This does not affect collectd (which is usually what you want). The collectd properties can be configured here:
     *  https://github.com/datastax/metric-collector-for-apache-cassandra/blob/a72e183/config/collectd.conf.tmpl#L14-L15
     */
    public static final int DEFAULT_WRITE_BUFFER_WATERMARK_LOW_IN_KB = Integer.getInteger("mcac.write_buffer_watermark_low_in_kb", 4096);
    public static final int DEFAULT_WRITE_BUFFER_WATERMARK_HIGH_IN_KB = Integer.getInteger("mcac.write_buffer_watermark_high_in_kb", 8192);

    /*
     * For metrics we add enhancing what exists out of the box for C* metrics
     */
    public static final MetricRegistry agentAddedMetricsRegistry = new MetricRegistry();

    private static final Logger logger = LoggerFactory.getLogger(UnixSocketClient.class);
    private static final int BATCH_SIZE = 256;
    private static final String FILTER_INSIGHTS_TAG = "mcac_filtered=true";
    private static final String INF_BUCKET = "bucket_inf";
    private static final long[] inputBuckets = new EstimatedHistogram(90).getBucketOffsets();
    private static final long[] decayingBuckets = new EstimatedHistogram(165).getBucketOffsets();

    // Log linear buckets (these must match the collectd entry in types.db)
    private static final Pair<Long, String>[] latencyBuckets;
    private static final long[] latencyOffsets = { 35, 60, 103, 179, 310, 535, 924, 1597, 2759, 4768, 8239, 14237,
            24601, 42510, 73457, 126934, 219342, 379022, 654949, 1131752, 1955666, 3379391, 5839588, 10090808,
            17436917 };

    static {
        latencyBuckets = new Pair[latencyOffsets.length];
        for (int i = 0; i < latencyBuckets.length; i++) {
            // Latencies are reported in nanoseconds, so we convert the offsets from micros
            // to nanos
            latencyBuckets[i] = Pair.create(latencyOffsets[i] * 1000, "bucket_" + Long.toString(latencyOffsets[i]));
        }
    }

    private final AtomicBoolean started;
    private final String socketFile;

    public void logError(String log, Throwable throwable) {
        logger.error(log, throwable);
    }

    @VisibleForTesting
    final ConcurrentHashMap<String, Function<String, Integer>> metricProcessors;
    @VisibleForTesting
    final ConcurrentHashMap<String, Function<String, Integer>> globalFilteredMetricProcessors;
    @VisibleForTesting
    final ConcurrentHashMap<String, Function<String, Integer>> insightFilteredMetricProcessors;

    private final TimeUnit rateUnit;
    private final TimeUnit durationUnit;
    private final double rateFactor;
    private final double durationFactor;
    private final String ip;
    private final Map<String, String> globalTags;

    private Bootstrap bootstrap;
    private EventLoopGroup eventLoopGroup;
    private volatile Configuration runtimeConfig = ConfigurationLoader.loadConfig();
    private final List<MetricRegistry> metricsRegistries;
    volatile Channel channel;
    private ScheduledFuture metricReportFuture;
    private ScheduledFuture eventReportFuture;
    private ScheduledFuture healthCheckFuture;
    private final AtomicLong successResponses;
    private final AtomicLong errorResponses;
    private final AtomicLong failedHealthChecks;
    private final AtomicLong metricReportingIntervalCount;
    private final AtomicLong eventReportingIntervalCount;
    private Long lastTokenRefreshNanos;

    private Method decayingHistogramOffsetMethod = null;

    public UnixSocketClient() {
        this(null, TimeUnit.SECONDS, TimeUnit.MICROSECONDS);
    }

    public UnixSocketClient(String socketFile, TimeUnit rateUnit, TimeUnit durationUnit) {
        this.metricsRegistries = Lists.newArrayList(CassandraMetricsRegistry.Metrics, agentAddedMetricsRegistry);
        this.started = new AtomicBoolean(false);
        this.socketFile = socketFile == null ? CollectdController.defaultSocketFile.get() : socketFile;
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.rateFactor = rateUnit.toSeconds(1);
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        this.ip = getBroadcastAddress().getHostAddress();
        this.globalTags = ImmutableMap.of("host", ip, "cluster", DatabaseDescriptor.getClusterName(), "datacenter",
                getDataCenter(), "rack", getRack());
        this.metricProcessors = new ConcurrentHashMap<>();
        this.globalFilteredMetricProcessors = new ConcurrentHashMap<>();
        this.insightFilteredMetricProcessors = new ConcurrentHashMap<>();
        this.metricReportFuture = null;
        this.healthCheckFuture = null;

        this.successResponses = new AtomicLong(0);
        this.errorResponses = new AtomicLong(0);
        this.failedHealthChecks = new AtomicLong(0);
        this.metricReportingIntervalCount = new AtomicLong(0);
        this.eventReportingIntervalCount = new AtomicLong(0);
        this.lastTokenRefreshNanos = 0L;
    }

    public static InetAddress getBroadcastAddress() {
        try {

            return DatabaseDescriptor.getBroadcastAddress() == null
                    ? DatabaseDescriptor.getListenAddress() == null ? InetAddress.getLocalHost()
                            : DatabaseDescriptor.getListenAddress()
                    : DatabaseDescriptor.getBroadcastAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getRack() {
        try {
            return (String) IEndpointSnitch.class.getMethod("getLocalRack")
                    .invoke(DatabaseDescriptor.getEndpointSnitch());
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException
                | IllegalAccessException e) {
            // No biggie
        }

        try {
            return (String) IEndpointSnitch.class.getMethod("getRack", InetAddress.class).invoke(DatabaseDescriptor.getEndpointSnitch(), getBroadcastAddress());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            logger.error("Couldn't determine rack", e);
            return "unknown_rack";
        }
    }

    public static String getDataCenter()
    {
        try
        {
            return (String) IEndpointSnitch.class.getMethod("getLocalDatacenter").invoke(DatabaseDescriptor.getEndpointSnitch());
        }
        catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e)
        {
            //No biggie
        }

        try {
            return (String) IEndpointSnitch.class.getMethod("getDatacenter", InetAddress.class).invoke(DatabaseDescriptor.getEndpointSnitch(), getBroadcastAddress());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            logger.error("Couldn't determine datacenter", e);
            return "unknown_dc";
        }
    }

    private EventLoopGroup epollGroup()
    {
        assert Epoll.isAvailable();
        EventLoopGroup epollEventLoopGroup = new EpollEventLoopGroup(1, new DefaultThreadFactory("insights"));

        bootstrap.group(epollEventLoopGroup).channel(EpollDomainSocketChannel.class)
                .handler(createNettyPipeline());

        return epollEventLoopGroup;
    }

    private ChannelInitializer<Channel> createNettyPipeline()
    {
        return new ChannelInitializer<Channel>()
        {
            @Override
            protected void initChannel(final Channel channel) throws Exception
            {
                channel.pipeline().addLast(new LineBasedFrameDecoder(256));
                channel.pipeline().addLast(new StringDecoder(CharsetUtil.US_ASCII));
                channel.pipeline().addLast(new StringEncoder(CharsetUtil.US_ASCII));
                channel.pipeline().addLast(new SimpleChannelInboundHandler<String>()
                {
                    @Override
                    protected void channelRead0(
                            ChannelHandlerContext ctx,
                            String msg
                    ) throws Exception
                    {
                        if (msg.startsWith("-1"))
                        {
                            NoSpamLogger.getLogger(logger, 5, TimeUnit.SECONDS).info("Collectd err: {}", msg);
                            UnixSocketClient.this.errorResponses.incrementAndGet();
                        }
                        else
                        {
                            logger.trace(msg);
                            UnixSocketClient.this.successResponses.incrementAndGet();
                        }
                    }
                });
            }
        };
    }

    public void start()
    {
        //Avoid being setting started to true until our setup is done
        synchronized (started)
        {
            if (!started.get())
            {
                bootstrap = new Bootstrap();

                if (SystemUtils.IS_OS_LINUX)
                {
                    eventLoopGroup = epollGroup();
                }
                else
                {
                    throw new RuntimeException("Unsupported OS");
                }

                try
                {
                    CollectdController.ProcessState r = CollectdController.instance.get().start(socketFile, runtimeConfig);

                    if (r != CollectdController.ProcessState.STARTED)
                        logger.warn("Not able to start collectd, will keep trying: {}", r);
                    else
                        tryConnect();
                }
                catch (Throwable t)
                {
                    logger.warn("Error connecting", t);
                }

                boolean applied = started.compareAndSet(false, true);
                assert applied;

                initMetricsReporting();
                initCollectdHealthCheck();
                restartMetricReporting(runtimeConfig.metric_sampling_interval_in_seconds);
            }
            else
            {
                throw new RuntimeException("MCAC Client is already started");
            }
        }
    }

    public void close()
    {
        synchronized (started)
        {
            if (started.compareAndSet(true, false))
            {
                logger.info("Stopping MCAC Client...");

                if (metricReportFuture != null)
                    metricReportFuture.cancel(true);

                if (healthCheckFuture != null)
                    healthCheckFuture.cancel(true);

                if (channel != null)
                {
                    channel.close().syncUninterruptibly();
                    channel = null;
                }

                if (eventLoopGroup != null)
                {
                    eventLoopGroup.shutdownGracefully();
                    eventLoopGroup = null;
                }

                CollectdController.instance.get().stop();
            }
            else
            {
                throw new RuntimeException("MCAC Client has already been stopped");
            }
        }
    }

    private void initCollectdHealthCheck()
    {
        //Use ScheduledExecutor vs Netty EventLoop since this blocks
        healthCheckFuture = ScheduledExecutors.scheduledTasks.scheduleWithFixedDelay(() -> {
            try
            {
                if (!CollectdController.instance.get().healthCheck())
                {
                    failedHealthChecks.incrementAndGet();
                    CollectdController.instance.get().start(socketFile, runtimeConfig);
                }

                tryConnect();
            }
            catch (Throwable e)
            {
                logger.error("Error with collectd healthcheck", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void tryConnect()
    {
        if (channel != null && channel.isOpen())
            return;

        try
        {
            channel = bootstrap.connect(new DomainSocketAddress(socketFile))
                    .syncUninterruptibly()
                    .channel();

            channel.config()
                    .setWriteBufferHighWaterMark(DEFAULT_WRITE_BUFFER_WATERMARK_HIGH_IN_KB * 1024)
                    .setWriteBufferLowWaterMark(DEFAULT_WRITE_BUFFER_WATERMARK_LOW_IN_KB * 1024);


            errorResponses.set(0);
            successResponses.set(0);

            logger.info("Connection to collectd established");
        }
        catch (Throwable t)
        {
            if (t instanceof IOException)
                logger.warn("Error connecting to collectd");
            else
                throw t;
        }
    }

    private synchronized void refreshFilters()
    {
        Iterator<Map.Entry<String, Function<String, Integer>>> entries;
        Set<String> seen = new HashSet<>();

        entries = Iterators.concat(
                metricProcessors.entrySet().iterator(),
                insightFilteredMetricProcessors.entrySet().iterator(),
                globalFilteredMetricProcessors.entrySet().iterator()
        );

        while (entries.hasNext())
        {
            Map.Entry<String, Function<String, Integer>> e = entries.next();

            if (!seen.add(e.getKey()))
                continue;

            entries.remove();
            addMetric(e.getKey(), e.getValue());
        }

        logger.info("unfiltered metrics {}, insight filtered metrics {}, globally filtered metrics {}",
                metricProcessors.size(), insightFilteredMetricProcessors.size(), globalFilteredMetricProcessors.size());
    }

    String clean(String name)
    {
        // Special case for coda hale metrics
        if (name.startsWith("jvm"))
        {
            name = name.replaceAll("\\-", "_");
            return name.toLowerCase();
        }

        name = name.replaceAll("\\s*,\\s*", ",");
        name = name.replaceAll("\\s+", "_");
        name = name.replaceAll("\\\\", "_");
        name = name.replaceAll("/", "_");

        name = name.replaceAll("[^a-zA-Z0-9\\.\\_]+", ".");
        name = name.replaceAll("\\.+", ".");
        name = name.replaceAll("_+", "_");

        //Convert camelCase to snake_case
        name = String.join("_", name.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));
        name = name.replaceAll("\\._", "\\.");
        name = name.replaceAll("_+", "_");

        return name.toLowerCase();
    }


    void addMetric(String name, Function<String, Integer> writer)
    {
        final FilteringRule.FilteringRuleMatch appliedCassandra = FilteringRule.applyFilters(name, runtimeConfig.filtering_rules);
        final FilteringRule.FilteringRuleMatch appliedCleaned = FilteringRule.applyFilters(clean(name), runtimeConfig.filtering_rules);

        final FilteringRule applied;
        //Prefer order (last wins)
        if(appliedCassandra.index != appliedCleaned.index)
        {
            applied = appliedCassandra.index > appliedCleaned.index ? appliedCassandra.rule : appliedCleaned.rule;
        }
        //and then prefer globally denied rules
        else if (appliedCassandra.rule.isAllowRule != appliedCleaned.rule.isAllowRule)
        {
            applied = appliedCassandra.rule.isAllowRule ? appliedCleaned.rule : appliedCassandra.rule;
        }
        else if (appliedCassandra.rule.isGlobal != appliedCleaned.rule.isGlobal)
        {
            applied = appliedCassandra.rule.isGlobal ? appliedCassandra.rule : appliedCleaned.rule;
        }
        else {
            applied = appliedCassandra.rule;
        }

        logger.debug("Using filtering rule {} for name '{}'", applied, name);
        ConcurrentHashMap<String, Function<String, Integer>> picked = null;

        if (applied.isAllowRule)
        {
            picked = applied.isGlobal ? metricProcessors : insightFilteredMetricProcessors;
        }
        else
        {
            picked = applied.isGlobal ? globalFilteredMetricProcessors : insightFilteredMetricProcessors;
        }

        picked.put(name, writer);
    }


    private void initMetricsReporting()
    {
        if (metricsRegistries == null)
            return;

        for(MetricRegistry metricRegistry : metricsRegistries)
        {
            try
            {
                metricRegistry.addListener(new MetricRegistryListener()
                {
                    void removeMetric(String name)
                    {
                        //Keep the last value of a metric when it's removed.
                        Function<String, Integer> f = metricProcessors.remove(name);
                        if (f != null && !StorageService.instance.isInShutdownHook())
                            f.apply("");

                        f = insightFilteredMetricProcessors.remove(name);
                        if (f != null && !StorageService.instance.isInShutdownHook())
                            f.apply(FILTER_INSIGHTS_TAG);

                        globalFilteredMetricProcessors.remove(name);
                    }

                    @Override
                    public void onGaugeAdded(String name, Gauge<?> gauge)
                    {
                        String cleanName = clean(name);
                        addMetric(name, (tags) -> writeMetric(cleanName, tags, gauge));
                    }

                    @Override
                    public void onGaugeRemoved(String name)
                    {
                        removeMetric(name);
                    }

                    @Override
                    public void onCounterAdded(String name, Counter counter)
                    {
                        String cleanName = clean(name);
                        addMetric(name, (tags) -> writeMetric(cleanName, tags, counter));
                    }

                    @Override
                    public void onCounterRemoved(String name)
                    {
                        removeMetric(name);
                    }

                    @Override
                    public void onHistogramAdded(String name, Histogram histogram)
                    {
                        String cleanName = clean(name);
                        addMetric(name, (tags) -> writeMetric(cleanName, tags, histogram));
                    }

                    @Override
                    public void onHistogramRemoved(String name)
                    {
                        removeMetric(name);
                    }

                    @Override
                    public void onMeterAdded(String name, Meter meter)
                    {
                        String cleanName = clean(name);
                        addMetric(name, (tags) -> writeMetric(cleanName, tags, meter));
                    }

                    @Override
                    public void onMeterRemoved(String name)
                    {
                        removeMetric(name);
                    }

                    @Override
                    public void onTimerAdded(String name, Timer timer)
                    {
                        String cleanName = clean(name);
                        addMetric(name, (tags) -> writeMetric(cleanName, tags, timer));
                    }

                    @Override
                    public void onTimerRemoved(String name)
                    {
                        removeMetric(name);
                    }
                });
            }
            catch (IllegalArgumentException e)
            {
                logger.warn("initializing Metrics Reporting failed with "+e.getMessage(), e);
            }
        }
    }

    private synchronized void restartMetricReporting(Integer metricSamplingIntervalInSeconds)
    {
        if (metricReportFuture != null)
            metricReportFuture.cancel(false);

        logger.info("Starting metric reporting with {} sec interval", metricSamplingIntervalInSeconds);

        //Some metrics are reported to insights as custom Insight types.
        //We avoid sending these at the same interval as other metrics since insights only needs things
        //at a 5 minute interval worst case. see InsightsRuntimeConfig::metricUpdateGapInSeconds
        final long reportInsightEvery = Math.max((long) Math.floor(runtimeConfig.metricUpdateGapInSeconds() / metricSamplingIntervalInSeconds), 1);
        logger.debug("Reporting metric insights every {} intervals", reportInsightEvery);

        metricReportFuture = eventLoopGroup.scheduleWithFixedDelay(() -> {

            if (channel == null || !channel.isOpen())
                logger.info("Metric reporting skipped due to connection to collectd not being established");

            long count = 0;
            long thisInterval = metricReportingIntervalCount.getAndIncrement();

            // Metric and Insight data
            // We only send insight data every N seconds defined above
            count += writeGroup(metricProcessors, thisInterval % reportInsightEvery == 0 ? "" : FILTER_INSIGHTS_TAG);

            // Metric data (Not sent to insight)
            count += writeGroup(insightFilteredMetricProcessors, FILTER_INSIGHTS_TAG);

            if (count > 0)
            {
                logger.trace("Calling flush with {}", count);
                flush();
            }
        }, metricSamplingIntervalInSeconds, metricSamplingIntervalInSeconds, TimeUnit.SECONDS);
    }

    private int writeGroup(ConcurrentHashMap<String, Function<String, Integer>> group, String tags)
    {
        int count = 0;

        for (Map.Entry<String, Function<String, Integer>> m : group.entrySet())
        {
            try
            {
                count += m.getValue().apply(tags);

                if (count >= BATCH_SIZE)
                {
                    logger.trace("Calling flush with {}", count);
                    flush();
                    count = 0;
                }
            }
            catch (Throwable t)
            {
                logger.warn("Error reporting: ", t);
            }
        }

        return count;
    }

    private int writeMetric(String name, String tags, Gauge gauge)
    {
        Object value = gauge.getValue();
        if (value instanceof Number)
        {
            reportCollectd(name, tags, (Number) value, "gauge");
        } else if (value instanceof long[])
        {
            /* DSP-18600: Instead of ignoring long[] gauges, we want to transform
            them into EstimatedHistograms. Because we want to treat it differently
            than a normal gauge we call a different method. */
            logger.trace("Treating Gauge {} long[] value as estimated histogram buckets", name);
            return writeMetric(name, tags, (long[]) value);
        } else
        {
            logger.trace("Value not a number {} {}", name, value);
            return 0;
        }

        return 1;
    }

    /**
     * Writes metrics assuming that the data is given int he "estimatedBuckets" format
     * used in several C* Gauges.
     */
    private int writeMetric(String name, String tags, long[] estimatedBuckets)
    {
        if (estimatedBuckets == null || (estimatedBuckets != null && estimatedBuckets.length == 0))
        {
            logger.trace("Gauge {} was empty or null, ignoring", name);
            return 0;
        }

        EstimatedHistogram hist = new EstimatedHistogram(estimatedBuckets);

        long count = hist.count();
        long max = hist.max();
        double mean = hist.mean();
        long min = hist.min();
        double stddev = -1.0;
        double p50 = hist.percentile(0.50);
        double p75 = hist.percentile(0.75);
        double p90 = hist.percentile(0.90);
        double p95 = hist.percentile(0.95);
        double p98 = hist.percentile(0.98);
        double p99 = hist.percentile(0.99);
        double p999 = hist.percentile(0.999);

        reportCollectdHistogram(
                name,
                FILTER_INSIGHTS_TAG,
                count,
                max,
                mean,
                min,
                stddev,
                p50,
                p75,
                p90,
                p95,
                p98,
                p99,
                p99);

        int sent = 1;

        if (!tags.contains(FILTER_INSIGHTS_TAG))
        {
            com.datastax.mcac.insights.metrics.Histogram h = new com.datastax.mcac.insights.metrics.Histogram(
                    name,
                    System.currentTimeMillis(),
                    globalTags,
                    hist.count(),
                    new SamplingStats(min, max, mean, p50, p75, p90, p98, p99, p999, stddev)
            );
            try
            {
                report(h);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            ++sent;
        }

        return sent;
    }


    private int writeMetric(String name, String tags, Counter counter)
    {
        reportCollectd(name, tags, counter.getCount(), "counter");
        return 1;
    }

    private int writeMetric(String name, String tags, Timer timer)
    {
        final Snapshot snapshot = timer.getSnapshot();

        // Lossy version of the stats
        double meanRate = convertRate(timer.getMeanRate());
        double min1Rate = convertRate(timer.getOneMinuteRate());
        double min5rate = convertRate(timer.getFiveMinuteRate());
        double min15rate = convertRate(timer.getFifteenMinuteRate());

        double max = convertDuration(snapshot.getMax());
        double mean = convertDuration(snapshot.getMean());
        double min = convertDuration(snapshot.getMin());
        double stddev = convertDuration(snapshot.getStdDev());
        double p50 = convertDuration(snapshot.getMedian());
        double p75 = convertDuration(snapshot.get75thPercentile());
        double p90 = convertDuration(snapshot.getValue(0.90));
        double p95 = convertDuration(snapshot.get95thPercentile());
        double p98 = convertDuration(snapshot.get98thPercentile());
        double p99 = convertDuration(snapshot.get99thPercentile());
        double p999 = convertDuration(snapshot.get999thPercentile());
        long count = timer.getCount();

        // Force all these to be filtered by insights since we send an equivalent event below
        reportCollectdHistogram(name, FILTER_INSIGHTS_TAG, count, max, mean, min, stddev, p50, p75, p90, p95, p98, p99, p999);
        reportCollectdMeter(name, FILTER_INSIGHTS_TAG, count, meanRate, min1Rate, min5rate, min15rate);
        Map<String, String> buckets = reportPrometheusTimer(name, FILTER_INSIGHTS_TAG, count, snapshot);
        int sent = 3;

        if (!tags.contains(FILTER_INSIGHTS_TAG))
        {
            com.datastax.mcac.insights.metrics.Timer t = new com.datastax.mcac.insights.metrics.Timer(
                    name,
                    System.currentTimeMillis(),
                    buckets,
                    timer.getCount(),
                    new SamplingStats((long) min, (long) max, mean, p50, p75, p95, p98, p99, p999, stddev),
                    new RateStats(min1Rate, min5rate, min15rate, meanRate),
                    rateUnit.name(),
                    durationUnit.name()
            );

            try
            {
                report(t);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            ++sent;
        }

        return sent;
    }

    private int writeMetric(String name, String tags, Meter meter)
    {
        double meanRate = convertRate(meter.getMeanRate());
        double min1Rate = convertRate(meter.getOneMinuteRate());
        double min5rate = convertRate(meter.getFiveMinuteRate());
        double min15rate = convertRate(meter.getFifteenMinuteRate());

        // Force all these to be filtered by insights since we send an equivalent event below
        reportCollectdMeter(name, FILTER_INSIGHTS_TAG, meter.getCount(), meanRate, min1Rate, min5rate, min15rate);

        int sent = 1;

        if (!tags.contains(FILTER_INSIGHTS_TAG))
        {
            // Also add custom insight
            com.datastax.mcac.insights.metrics.Meter m = new com.datastax.mcac.insights.metrics.Meter(
                    name,
                    System.currentTimeMillis(),
                    globalTags,
                    meter.getCount(),
                    new RateStats(min1Rate, min5rate, min15rate, meanRate),
                    rateUnit.name()
            );

            try
            {
                report(m);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            ++sent;
        }

        return sent;
    }

    /** Do not convertDuration on Histograms, they are not time based
     * and converting will lose the values entirely if max and min are truncated.
     */
    private int writeMetric(String name, String tags, Histogram histogram)
    {
        final Snapshot snapshot = histogram.getSnapshot();

        long max = snapshot.getMax();
        double mean = snapshot.getMean();
        long min = snapshot.getMin();
        double stddev = snapshot.getStdDev();
        double p50 = snapshot.getMedian();
        double p75 = snapshot.get75thPercentile();
        double p90 = snapshot.getValue(0.90);
        double p95 = snapshot.get95thPercentile();
        double p98 = snapshot.get98thPercentile();
        double p99 = snapshot.get99thPercentile();
        double p999 = snapshot.get999thPercentile();

        // Force all these to be filtered by insights since we send an equivalent event below
        reportCollectdHistogram(name, FILTER_INSIGHTS_TAG, histogram.getCount(), max, mean, min, stddev, p50, p75, p90, p95, p98, p99, p999);
        int sent = 1;

        if (!tags.contains(FILTER_INSIGHTS_TAG))
        {
            com.datastax.mcac.insights.metrics.Histogram h = new com.datastax.mcac.insights.metrics.Histogram(
                    name,
                    System.currentTimeMillis(),
                    globalTags,
                    histogram.getCount(),
                    new SamplingStats(min, max, mean, p50, p75, p95, p98, p99, p999, stddev)
            );

            try
            {
                report(h);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            ++sent;
        }

        return sent;
    }

    double convertDuration(double duration)
    {
        return duration * durationFactor;
    }

    double convertRate(double rate)
    {
        return rate * rateFactor;
    }

    public long getSuccessResponses()
    {
        return successResponses.get();
    }

    public long getErrorResponses()
    {
        return errorResponses.get();
    }

    public long getFailedHealthChecks()
    {
        return failedHealthChecks.get();
    }



    // See https://collectd.org/wiki/index.php/Plain_text_protocol#PUTVAL
    boolean reportCollectdHistogram(String name, String tags, long count, double max, double mean, double min, double stddev,
            double p50, double p75, double p90, double p95, double p98, double p99, double p999)
    {
        int n = runtimeConfig.metric_sampling_interval_in_seconds;
        String msg = new StringBuilder(256)
                .append(ip).append("/mcac-")
                .append(name).append("/")
                .append("histogram interval=")
                .append(n).append(" ")
                .append(tags).append(" N:")
                .append(count).append(":")
                .append(max).append(":")
                .append(mean).append(":")
                .append(min).append(":")
                .append(stddev).append(":")
                .append(p50).append(":")
                .append(p75).append(":")
                .append(p90).append(":")
                .append(p95).append(":")
                .append(p98).append(":")
                .append(p99).append(":")
                .append(p999)
                .toString();

        return reportInternalWithoutFlush("PUTVAL", msg);
    }

    // See https://collectd.org/wiki/index.php/Plain_text_protocol#PUTVAL
    boolean reportCollectdMeter(String name, String tags, long count, double meanRate, double min1Rate, double min5Rate, double min15Rate)
    {
        int n = runtimeConfig.metric_sampling_interval_in_seconds;
        String msg = new StringBuilder(256)
                .append(ip).append("/mcac-")
                .append(name).append("/")
                .append("meter interval=")
                .append(n).append(" ")
                .append(tags).append(" N:")
                .append(count).append(":")
                .append(meanRate).append(":")
                .append(min1Rate).append(":")
                .append(min5Rate).append(":")
                .append(min15Rate)
                .toString();

        return reportInternalWithoutFlush("PUTVAL", msg);
    }

    /**
     * Converts our latency metrics into a prometheus compatible ones
     * https://www.robustperception.io/why-are-prometheus-histograms-cumulative
     * https://prometheus.io/docs/practices/histograms
     *
     * @return buckets as tags for Insights use.
     */
    Map<String, String> reportPrometheusTimer(String name, String tags, long count, Snapshot snapshot)
    {
        int n = runtimeConfig.metric_sampling_interval_in_seconds;
        StringBuilder msg = new StringBuilder(512)
                .append(ip).append("/mcac-")
                .append(name).append("/")
                .append("micros interval=")
                .append(n).append(" ")
                .append(tags).append(" N:")
                .append(count).append(":")
                .append(snapshot.getMean() * count); //calculate the sum from the avg


        Map<String, String> bucketTags = Maps.newHashMapWithExpectedSize(globalTags.size() + latencyBuckets.length);
        bucketTags.putAll(globalTags);

        long[] buckets = inputBuckets;
        long[] values = snapshot.getValues();
        String snapshotClass = snapshot.getClass().getName();

        if (snapshotClass.contains("EstimatedHistogramReservoirSnapshot"))
        {
            buckets = decayingBuckets;
        }
        else if (snapshotClass.contains("DecayingEstimatedHistogram"))
        {
            try
            {
                Method m = decayingHistogramOffsetMethod;

                if (m == null)
                {
                    m = snapshot.getClass().getMethod("getOffsets");
                    decayingHistogramOffsetMethod = m;
                }

                buckets = (long[]) m.invoke(snapshot);
            }
            catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
            {
                //nothing we can do
            }
        }

        // This can happen if histogram isn't EstimatedDecay or EstimatedHistogram
        if (values.length != buckets.length)
        {
            NoSpamLogger.getLogger(logger, 1, TimeUnit.HOURS)
                    .info("Not able to get buckets for {} {} type {}", name, values.length, snapshot.getClass().getName());
            return bucketTags;
        }

        int outputIndex = 0; //output index
        long cumulativeCount = 0;
        for (int i = 0; i < values.length; i++)
        {
            //Hit bucket edge
            if (outputIndex < latencyBuckets.length && buckets[i] > latencyBuckets[outputIndex].left)
            {
                String value = Long.toString(cumulativeCount);
                msg.append(":").append(value);
                bucketTags.put(latencyBuckets[outputIndex++].right, value);
            }

            cumulativeCount += values[i];
        }

        String total = Long.toString(cumulativeCount);

        //Add any missing buckets + inf bucket
        while (outputIndex++ <= latencyBuckets.length)
        {
            msg.append(":").append(total);
            bucketTags.put(INF_BUCKET, total);
        }

        reportInternalWithoutFlush("PUTVAL", msg.toString());

        return bucketTags;
    }


    // See https://collectd.org/wiki/index.php/Plain_text_protocol#PUTVAL
    boolean reportCollectd(String name, String tags, Number value, String type, String typeInstance)
    {
        int n = runtimeConfig.metric_sampling_interval_in_seconds;
        String msg = new StringBuilder(256)
                .append(ip).append("/mcac-")
                .append(name).append("/")
                .append(type).append("-")
                .append(typeInstance).append(" ")
                .append("interval=").append(n).append(" ")
                .append(tags).append(" N:")
                .append(value)
                .toString();

        return reportInternalWithoutFlush("PUTVAL", msg);
    }

    // See https://collectd.org/wiki/index.php/Plain_text_protocol#PUTVAL
    boolean reportCollectd(String name, String tags, Number value, String type)
    {
        int n = runtimeConfig.metric_sampling_interval_in_seconds;
        String msg = new StringBuilder(256)
                .append(ip).append("/mcac-")
                .append(name).append("/")
                .append(type).append(" ")
                .append("interval=").append(n).append(" ")
                .append(tags).append(" N:")
                .append(value)
                .toString();

        return reportInternalWithoutFlush("PUTVAL", msg);
    }

    @VisibleForTesting
    boolean reportInternalWithFlush(String collectdAction, String insightJsonString)
    {
        return reportInternal(collectdAction, insightJsonString, true);
    }

    @VisibleForTesting
    boolean reportInternalWithoutFlush(String collectdAction, String insightJsonString)
    {
        if(channel == null)
        {
            return false;
        }

        if (started.get())
        {
            ChannelOutboundBuffer buf = channel.unsafe().outboundBuffer();
            // if we can access ChannelOutboundBuffer, flush before the channel becomes unwritable
            if (channel != null && buf != null)
            {
                if (buf.bytesBeforeUnwritable() < channel.config().getWriteBufferHighWaterMark() * 0.1)
                {
                    flush();
                }
            }
            // fallback in case ChannelOutboundBuffer is not accessible
            else if (channel != null)
            {
                flush();
            }
        }

        return reportInternal(collectdAction, insightJsonString, false);
    }

    boolean reportInternal(String collectdAction, String insightJsonString, boolean flush)
    {
        if (started.get())
        {
            if (insightJsonString.contains("\n"))
            {
                insightJsonString = insightJsonString.replaceAll("\\n", "");
            }

            if (channel == null || !channel.isOpen())
            {
                NoSpamLogger.getLogger(logger, 30, TimeUnit.SECONDS).warn("Connection to Collectd not established");
                return false;
            }

            try
            {
                channel.write(collectdAction + " " + insightJsonString + "\n");
                if (flush)
                    channel.flush();
            }
            catch (Throwable t)
            {
                if (t instanceof IOException)
                {
                    if (channel != null)
                        channel.close().syncUninterruptibly();

                    channel = null;
                    NoSpamLogger.getLogger(logger, 30, TimeUnit.SECONDS).info("Channel closed: ", t);
                }
                else if (t instanceof RejectedExecutionException)
                {
                    if (eventLoopGroup.isShutdown())
                    {
                        NoSpamLogger.getLogger(logger, 30, TimeUnit.SECONDS).error("Insights reporter eventloop shutdown");
                    }
                    else
                    {
                        NoSpamLogger.getLogger(logger, 30, TimeUnit.SECONDS).info("Insight write queue full, dropping report");
                    }
                }
                else
                {
                    logger.warn("Exception encountered reporting insight", t);
                }

                return false;
            }

            return true;
        }
        else
        {
            return false;
        }
    }

    void flush()
    {
        if (channel == null || !channel.isOpen())
        {
            NoSpamLogger.getLogger(logger, 30, TimeUnit.SECONDS).warn("Connection to Collectd not established");
            return;
        }

        try
        {
            channel.flush();
        }
        catch (Throwable t)
        {
            if (t instanceof IOException)
            {
                if (channel != null)
                    channel.close().syncUninterruptibly();

                channel = null;
            }
            else
            {
                throw t;
            }
        }
    }


    public boolean report(com.datastax.mcac.insights.metrics.Metric metricInsight) throws Exception
    {
        return reportInternalWithFlush(
                "PUTINSIGHT",
                JacksonUtil.writeValueAsString(metricInsight)
        );
    }

    public boolean report(Insight insight) throws Exception
    {
        return reportInternalWithFlush(
                "PUTINSIGHT",
                JacksonUtil.writeValueAsString(insight)
        );
    }


    public void onConfigChanged(
            Configuration previousConfig,
            Configuration newConfig
    )
    {
        runtimeConfig = newConfig;

        synchronized (started)
        {
            if (!started.get())
                return;

            try
            {
                if (CollectdController.instance.get().reloadPlugin(newConfig)
                        == CollectdController.ProcessState.STARTED)
                {
                    reportInternalWithFlush("RELOADINSIGHTS", "");
                }
            }
            catch (Exception e)
            {
                logger.warn("Problem reloading insights config for collectd", e);
            }

            if (!Objects.equals(previousConfig.metric_sampling_interval_in_seconds, newConfig.metric_sampling_interval_in_seconds)
                    || !Objects.equals(previousConfig.upload_interval_in_seconds, newConfig.upload_interval_in_seconds))
            {
                restartMetricReporting(newConfig.metric_sampling_interval_in_seconds);
            }

            if (!Objects.equals(previousConfig.filtering_rules, newConfig.filtering_rules))
            {
                refreshFilters();
            }
        }
    }
}
