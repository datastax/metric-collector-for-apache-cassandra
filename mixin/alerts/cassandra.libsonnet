{
  _config+:: {
    cassandraSelector: error 'must provide selector for Cassandra alerts',
    dimensions: error 'must provide dimensions for Cassandra alerts',
    alerts: {
      selector: $._config.cassandraSelector,
      dimensions: std.join(', ', $._config.dimensions),
    },
  },
  prometheusAlerts+:: {
    groups+: [
      local source = std.join('/', [
        '{{$labels.%s}}' % label
        for label in $._config.dimensions
      ]);
      {
        name: 'metric-collector-for-apache-cassandra',
        rules: [
          {
            alert: 'CassandraReadLatencyHigh',
            annotations: {
              summary: 'Cassandra has high latency for read requests.',
              description: 'Cassandra %s has a 99th percentile latency of {{$value * 10^-6 | humanizeDuration}} for read requests.' % source,
            },
            expr: 'histogram_quantile(0.99, sum by (%(dimensions)s, le) (rate(mcac_client_request_latency_bucket{%(selector)s, request_type="read"}[5m])) > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraWriteLatencyHigh',
            annotations: {
              summary: 'Cassandra has high latency for write requests.',
              description: 'Cassandra %s has a 99th percentile latency of {{$value * 10^-6 | humanizeDuration}} for write requests.' % source,
            },
            expr: 'histogram_quantile(0.99, sum by (%(dimensions)s, le) (rate(mcac_client_request_latency_bucket{%(selector)s, request_type="write"}[5m])) > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableReadLatencyHigh',
            annotations: {
              summary: 'Cassandra table has high latency for read requests.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a 99th percentile latency of {{$value * 10^-6 | humanizeDuration}} for read requests.' % source,
            },
            expr: 'histogram_quantile(0.99, sum by (%(dimensions)s, keyspace, table, le) (rate(mcac_table_read_latency_bucket{%(selector)s}[5m])) > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableWriteLatencyHigh',
            annotations: {
              summary: 'Cassandra table has high latency for write requests.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a 99th percentile latency of {{$value * 10^-6 | humanizeDuration}} for write requests.' % source,
            },
            expr: 'histogram_quantile(0.99, sum by (%(dimensions)s, keyspace, table, le) (rate(mcac_table_write_latency_bucket{%(selector)s}[5m])) > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          // FIXME: Quantiles calculated client-side cannot be aggregated
          // Histogram bucket metrics need to be fixed to work with histogram_quantile() server-side
          // https://prometheus.io/docs/practices/histograms/
          // https://github.com/datastax/metric-collector-for-apache-cassandra/issues/56
          {
            alert: 'CassandraCrossNodeLatencyHigh',
            annotations: {
              summary: 'Cassandra has high internode latency.',
              description: 'Cassandra %s has a 99th percentile internode latency of {{$value * 10^-6 | humanizeDuration}}.' % source,
            },
            expr: 'collectd_mcac_histogram_p99{%(selector)s, mcac="org.apache.cassandra.metrics.messaging.cross_node_latency"} > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraDatacenterLatencyHigh',
            annotations: {
              summary: 'Cassandra has high datacenter latency.',
              description: 'Cassandra %s has a 99th percentile latency of {{$value * 10^-6 | humanizeDuration}} for datacenter {{$labels.datacenter}}.' % source,
            },
            expr: |||
              label_replace(
                collectd_mcac_histogram_p99{%(selector)s, mcac=~"org\\.apache\\.cassandra\\.metrics\\.messaging\\.(\\w+)\\.latency"} > 200000,
                "datacenter", "$1", "mcac", "org\\.apache\\.cassandra\\.metrics\\.messaging\\.(\\w+)\\.latency"
              )
            |||,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraPendingCompactionTasks',
            annotations: {
              summary: 'Cassandra has pending compaction tasks.',
              description: 'Cassandra %s has {{$value | humanize}} pending compaction tasks.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_compaction_pending_tasks{%(selector)s}[5m])) > 15' % $._config.alerts,
            'for': '30m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableDroppedMutations',
            annotations: {
              summary: 'Cassandra table has dropped mutations.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has dropped {{$value | humanize}} mutations.' % source,
            },
            expr: 'sum by (%(dimensions)s, keyspace, table) (rate(mcac_table_dropped_mutations_total{%(selector)s}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTablePartitionSizeLarge',
            annotations: {
              summary: 'Cassandra table has large partitions.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a {{$value | humanize1024}}iB large partition.' % source,
            },
            expr: 'mcac_table_max_partition_size{%(selector)s} > 100 * 1024^2',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableLiveSSTableCountHigh',
            annotations: {
              summary: 'Cassandra table has a high count of SSTables.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has {{$value | humanize}} SSTables.' % source,
            },
            expr: 'sum by (%(dimensions)s, keyspace, table) (mcac_table_live_ss_table_count{%(selector)s}) > 200' % $._config.alerts,
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraStoredHintsHigh',
            annotations: {
              summary: 'Cassandra stored hints is high.',
              description: 'Cassandra %s has stored {{$value | humanize}} hints. Some nodes are not reachable.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_storage_total_hints_total{%(selector)s}[5m])) > 0',
            'for': '15m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraHintsReplayFailed',
            annotations: {
              summary: 'Cassandra has failed hint replays.',
              description: 'Cassandra %s has failed {{$value | humanize}} hint replays.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_hints_hints_failed_total{%(selector)s}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraHintsReplayTimedOut',
            annotations: {
              summary: 'Cassandra has failed hints replays.',
              description: 'Cassandra %s has timed out {{$value | humanize}} hint replays.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_hints_hints_timed_out_total{%(selector)s}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraMemtableBlockedOnAllocation',
            annotations: {
              summary: 'Cassandra Memtable has blocked on allocation.',
              description: 'Cassandra Memtable %s has blocked on allocation {{$value | humanize}} times.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_memtable_pool_blocked_on_allocation_total{%(selector)s, kind="meter"}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraMemtableBlockedFlushWriterTasks',
            annotations: {
              summary: 'Cassandra Memtable has blocked flush writer tasks.',
              description: 'Cassandra Memtable %s has {{$value | humanize}} blocked flush writer tasks.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_thread_pools_currently_blocked_tasks_total{%(selector)s, pool_type="internal", pool_name="memtable_flush_writer"}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraMemtableBlockedCompactorExecutorTasks',
            annotations: {
              summary: 'Cassandra Memtable has blocked compactor exectutor tasks.',
              description: 'Cassandra Memtable %s has {{$value | humanize}} blocked compactor exectutor tasks.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_thread_pools_currently_blocked_tasks_total{%(selector)s, pool_type="internal", pool_name="compaction_executor"}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraAbortedCompactionTasks',
            annotations: {
              summary: 'Cassandra has aborted compaction tasks.',
              description: 'Cassandra %s has aborted {{$value | humanize}} compaction tasks.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_compaction_compactions_aborted_total{%(selector)s}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraJVMGarbageCollectionTimeHigh',
            annotations: {
              summary: 'Cassandra has high JVM garbage collection time',
              description: 'Cassandra JVM garbage collector {{$labels.collector_type}} in %s has taken {{$value * 10^-6 | humanizeDuration}}.' % source,
            },
            expr: 'mcac_jvm_gc_time{%(selector)s} > 200000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraSegmentsWaitingOnCommit',
            annotations: {
              summary: 'Cassandra has segments waiting on commits.',
              description: 'Cassandra %s has {{$value | humanize}} segments waiting on commits.' % source,
            },
            expr: 'sum by (%(dimensions)s) (rate(mcac_commit_log_waiting_on_commit_total{%(selector)s, kind="meter"}[5m])) > 100' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraSegmentsWaitingOnCommitDurationHigh',
            annotations: {
              summary: 'Cassandra has spent a long time waiting on commits.',
              description: 'Cassandra %s has a 99th percentile waiting duration of {{$value * 10^-6 | humanizeDuration}} for commits.' % source,
            },
            expr: 'histogram_quantile(0.99, sum by (%(dimensions)s, le) (rate(mcac_commit_log_waiting_on_commit_bucket{%(selector)s}[5m])) > 10000' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTablePendingFlushes',
            annotations: {
              summary: 'Cassandra table has pending flushes.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has {{$value | humanize}} pending flushes.' % source,
            },
            expr: 'sum by (%(dimensions)s, keyspace, table) (rate(mcac_table_pending_flushes_total{%(selector)s}[5m])) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableKeyCacheHitRateLow',
            annotations: {
              summary: 'Cassandra table has a low key cache hit rate.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has {{$value | humanizePercentage}}%% key cache hit rate.' % source,
            },
            expr: 'sum by (%(dimensions)s, keyspace, table) (mcac_table_key_cache_hit_rate{%(selector)s}) > 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTargetUnreachable',
            annotations: {
              summary: 'Prometheus cannot scrape metrics from Cassandra nodes.',
              description: 'Prometheus cannot scrape metrics from Cassandra nodes in %s.' % source,
            },
            expr: 'up{%(selector)s} == 0' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTargetTooManyUnreachable',
            annotations: {
              summary: 'Prometheus cannot scrape metrics from Cassandra nodes',
              description: 'Prometheus cannot scrape metrics from Cassandra nodes in %s.' % source,
            },
            expr: 'count(up{%(selector)s} == 0) by (%(dimensions)s) > 1' % $._config.alerts,
            'for': '1m',
            labels: {
              severity: 'critical',
            },
          },
          {
            alert: 'CassandraCPUUsageHigh',
            annotations: {
              summary: 'Cassandra has high CPU usage.',
              description: 'Cassandra node {{$labels.instance}} in %s has {{$value | humanizePercentage}}%% CPU usage.' % source,
            },
            expr: |||
              sum(rate(collectd_cpu_total{%(selector)s, type="user"}[5m])) by (%(dimensions)s, instance)
                /
              sum(rate(collectd_cpu_total{%(selector)s}[5m])) by (%(dimensions)s, instance) >= 100
            ||| % $._config.alerts,
            'for': '1h',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraLoadHigh',
            annotations: {
              summary: 'Cassandra has high CPU usage.',
              description: 'Cassandra node {{$labels.instance}} in %s has {{$value | humanize}} load.' % source,
            },
            expr: |||
              collectd_load_shortterm
                > on (%(dimensions)s, instance)
              0.7 * count(collectd_cpu_total{%(selector)s, type="system"}) by (%(dimensions)s, instance)
            ||| % $._config.alerts,
            'for': '1h',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraDiskUsageHigh',
            annotations: {
              summary: 'Cassandra has high disk usage.',
              description: 'Cassandra node {{$labels.instance}} in %s has {{$value | humanizePercentage}}%% usage for volume {{$labels.df}}.' % source,
            },
            expr: |||
              collectd_df_df_complex{%(selector)s, type="used"}
                / on((%(dimensions)s, instance, df)
              collectd_df_df_complex{%(selector)s, type="free"} > 0.5
            ||| % $._config.alerts,
            'for': '4h',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableSSTablesPerReadHigh',
            annotations: {
              summary: 'Cassandra table has high number of SSTables accessed per read.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a 99th percentile of {{$value | humanize}} SSTables accessed per read.' % source,
            },
            expr: 'mcac_keyspace_ss_tables_per_read_histogram{%(selector)s, quantile=".99"} > 10' % $._config.alerts,
            'for': '1d',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTableTombstonesPerReadHigh',
            annotations: {
              summary: 'Cassandra table has high number of tombstones scanned per read.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a 99th percentile of {{$value | humanize}} tombstones scanned per read.' % source,
            },
            expr: 'mcac_table_tombstone_scanned_histogram{%(selector)s, quantile=".99"} > 1000' % $._config.alerts,
            'for': '1d',
            labels: {
              severity: 'warning',
            },
          },
          {
            alert: 'CassandraTablePartitionSize99thPercentileLarge',
            annotations: {
              summary: 'Cassandra table has large partitions.',
              description: 'Cassandra table `{{$labels.keyspace}}.{{$labels.table}}` in %s has a 99th percentile of {{$value | humanize1024}}iB partition size.' % source,
            },
            expr: 'mcac_table_estimated_partition_size_histogram{%(selector)s, quantile=".99"} > 200 * 1024^2' % $._config.alerts,
            'for': '1d',
            labels: {
              severity: 'warning',
            },
          },
        ],
      },
    ],
  },
}
