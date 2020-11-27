local grafana = (import 'grafonnet/grafana.libsonnet')
              + (import 'grafonnet-polystat-panel/plugin.libsonnet');

local dashboard = grafana.dashboard;
local prometheus = grafana.prometheus;
local template = grafana.template;
local row = grafana.row;
local polystatPanel = grafana.polystatPanel;

local graphPanel = grafana.graphPanel;
local textPanel = grafana.text;

local prefix = std.extVar('prefix');

local fillMinMaxSeriesOverrides = {
    'alias': 'max',
    'fillBelowTo': 'min',
    'lines': false
};

local removeMinlineSeriesOverrides = {
    'alias': 'min',
    'lines': false
};

// used in the single stat panels where higher is better - cache hit rates for example
local reversedColors =[
 '#d44a3a',
 'rgba(237, 129, 40, 0.89)',
 '#299c46',
];

dashboard.new(
  'Cassandra Read Path',
  schemaVersion=14,
  refresh='30s',
  time_from='now-30m',
  editable=true,
  tags=['Cassandra', 'Read', 'Read-Path', 'Select'],
  style='dark'
)
.addTemplate(
  grafana.template.datasource(
    'PROMETHEUS_DS',
    'prometheus',
    'Prometheus',
    hide='all',
  )
)
.addTemplate(
  template.new(
    'cluster',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{}, cluster)',
    label='Cluster',
    refresh='time',
  )
)
.addTemplate(
  template.new(
    'dc',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster="$cluster"}, dc)',
    label='DataCenter',
    refresh='time',
    multi=true,
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'rack',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster="$cluster", dc=~"$dc"}, rack)',
    label='Rack',
    refresh='time',
    multi=true,
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'node',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster="$cluster", dc=~"$dc", rack=~"$rack"}, instance)',
    label='Node',
    refresh='time',
    multi=true,
    includeAll=true,
    allValues=".*",
  )
)
.addRow(
  row.new(title='', height='50px')
  .addPanel(textPanel.new(transparent=true))
  .addPanel(
    textPanel.new(
      transparent=true,
      mode="html",
      content='<a href="https://cassandra.apache.org" target="new"><img src="https://cassandra.apache.org/img/cassandra_logo.png"/></a>',
    )
  )
  .addPanel(textPanel.new(transparent=true))
)
.addRow(
  row.new(title='Local Reads Throughputs (Node Perspective)')
  .addPanel(
    graphPanel.new(
      'Local Reads Throughput per Table',
      description='Total reads, ranges and cas_prepare per table',
      format='rps',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_read_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Reads: {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_range_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Ranges: {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_cas_prepare_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Prepare: {{keyspace}}.{{table}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Local Reads Throughput per Node',
      description='Total reads, ranges and cas_prepare per node',
      format='rps',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_read_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Reads: {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_range_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Ranges: {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_cas_prepare_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Prepare: {{instance}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Local Reads Throughput per Node and Table',
      description='Total reads, ranges and cas_prepare per node and table',
      format='rps',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_read_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Reads: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_range_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Ranges: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_cas_prepare_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Prepare: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
  )
)


.addRow(
  row.new(title='Local Reads Latencies')

  .addPanel(
    graphPanel.new(
      'Local Read Latency per Table',
      description='Read latency for local reads per table (98 - 999th percentile)',
      format='µs',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.98, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p999 - {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Local Read Latency per Node',
      description='Read latency for local reads per node (98 - 999th percentile)',
      format='µs',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.98, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p999 - {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Local Read Latency per Table and Node',
      description='Read latency for local reads per table and per node  (98 - 999th percentile)',
      format='µs',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.98, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{keyspace}}.{{table}} - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{keyspace}}.{{table}} - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_read_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p999 - {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )
)

.addRow(
  row.new(title='Thread Pools')
  .addPanel(
    graphPanel.new(
      'Pending Tasks per Node',
      description='Pending threads per node, by thread pool name filtering threads possibly impacting reads',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, pool_name) (' + prefix + '_thread_pools_pending_tasks{pool_name=~"read_stage|request_response_stage|internal_response_stage|compaction_executor", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{instance}} - pending {{pool_name}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Blocked Tasks per Node',
      description='Pending threads per node, by thread pool name filtering threads possibly impacting reads',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, pool_name) (' + prefix + '_thread_pools_total_blocked_tasks_total{pool_name=~"read_stage|request_response_stage|internal_response_stage|compaction_executor", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{instance}} - blocked {{pool_name}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Dropped Messages per Node',
      description='Pending threads per node, by thread pool name filtering threads possibly impacting reads',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, message_type) (rate(' + prefix + '_dropped_message_dropped_total{message_type=~"read|request_response|range_slice|paged_range",cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{instance}} - dropped {{message_type}}',
      )
    )
  )
)


.addRow(
  row.new(title='Tombstones Reads')

  .addPanel(
    graphPanel.new(
      'Scanned Tombstones per Table',
      description='Sum of tombstones scanned per minute, for each table',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance) (' + prefix + '_table_tombstone_scanned_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max scanned tombstones: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Scanned Tombstones per Node',
      description='Sum number of tombstones scanned per minute, for each node',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance) (' + prefix + '_table_tombstone_scanned_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max scanned tombstones: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Scanned Tombstones per Table and Node',
      description='Number of tombstones scanned per minute, for each combination of table and node',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_tombstone_scanned_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max scanned tombstones: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )


)


.addRow(
  row.new(title='SStable Hits per Read')

  .addPanel(
    graphPanel.new(
      'SSTable Hits per Read - Max per Table',
      description='Max of SStable hit per read, for each table',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, keyspace, table) (' + prefix + '_table_ss_tables_per_read_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max SSTables hit per read: {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'SSTable Hits per Read - Max per Node',
      description='Max of SSTable hit per read, for each node',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance) (' + prefix + '_table_ss_tables_per_read_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max SSTables hit per read: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'SSTable Hit per Read - Max per Table and Node',
      description='Max of SSTable hit per read, for each combination of table and node',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_ss_tables_per_read_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max SSTables hit per read: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )

)


.addRow(
  row.new(title='Max & Average Partition Size')

  .addPanel(
    graphPanel.new(
      'Max & Average Partition Size per Table',
      description='Max & Average of the partition sizes, for each table',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, keyspace, table) (' + prefix + '_table_estimated_partition_size_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max partition size: {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster, keyspace, table) (' + prefix + '_table_estimated_partition_size_histogram{quantile=".50", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Avg partition size: {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Max & Average Partition Size per Node',
      description='Max & Average of the partition sizes, for each node',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance) (' + prefix + '_table_estimated_partition_size_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max partition size: {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster, dc, rack, instance) (' + prefix + '_table_estimated_partition_size_histogram{quantile=".50", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Avg partition size: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Max & Average Partition Size per Table and Node',
      description='Max & Average of the partition sizes, for each combination of table and node',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_estimated_partition_size_histogram{quantile="1", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max partition size: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_estimated_partition_size_histogram{quantile=".50", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Avg partition size: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )

)

.addRow(
  row.new(title='Caches (collected at the table level)')

  .addPanel(
    graphPanel.new(
      'KeyCache Hit Rate - Min per Table',
      description='Min of keyCache hit rates, for each table',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, keyspace, table) (' + prefix + '_table_key_cache_hit_rate{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Min keyCache hit rate: {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'KeyCache Hit Rate - Min per Node',
      description='Min of keyCache hit rates, for each node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance) (' + prefix + '_table_key_cache_hit_rate{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Min keyCache hit rate: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'KeyCache Hit Rate - Min per Table and Node',
      description='Min of keyCache hit rates, for each combination of table and node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_key_cache_hit_rate{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Min keyCache hit rate: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )

  /*
  // TODO verify this is correct metrics. Row cache (table levels) are exposed in a quite different way than keycache and we have to ensure calculations below are correct!
  .addPanel(
    graphPanel.new(
      'RowCache Hit Rate - Min per Table',
      description='Min of RowCache hit rates, for each table',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, keyspace, table) (
          (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          / ignoring (mcac)
          (
            (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_miss_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_hit_out_of_range_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          )
        )',
        legendFormat='Min rowCache hit rate: {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'RowCache Hit Rate - Min per Node',
      description='Min of RowCache hit rates, for each node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance) (
          (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          / ignoring (mcac)
          (
            (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_miss_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_hit_out_of_range_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          )
        )',
        legendFormat='Min rowCache hit rate: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'RowCache Hit Rate - Min per Table and Node',
      description='Min of RowCache hit rates, for each combination of table and node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance, keyspace, table) (
          (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          / ignoring (mcac)
          (
            (rate(' + prefix + '_table_row_cache_hit_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_miss_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
            + ignoring (mcac) (rate(' + prefix + '_table_row_cache_hit_out_of_range_total{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))
          )
        )',
        legendFormat='Min rowCache hit rate: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )
*/

)


.addRow(
  row.new(title='Key Caches Hit Rate (collected at the node level)')

  .addPanel(
    graphPanel.new(
      'Caches Hit Rates - per Node',
      description='Caches hit rates, for each node and cache type',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='(' + prefix + '_cache_hit_rate{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{cache_name}} hit rate for {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Caches sizes and capacities - per Node',
      description='Caches sizes and capacities, for each node and cache type',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='(' + prefix + '_cache_size{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{cache_name}} size {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='(' + prefix + '_cache_capacity{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{cache_name}} capacity {{instance}}',
      )
    )
  )

  .addPanel(
    polystatPanel.new(
      'Caches hit rates per cache type',
      description='Nodes Status uses Internal/Gossip activity. Be mindful that if Native or Thrift protocol are disabled, the nodes won\'t be reachable, and still marked up',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      span=12,
      global_unit_format='percentunit',
      // global_unit_decimals='2',
      global_operator_name='current',
      global_thresholds=[
        {
          "value": 0,
          "state": 2,
          "color": "#d44a3a"
        },
        {
          "value": 0.5,
          "state": 1,
          "color": "#ED8128"
        },
        {
          "value": 0.85,
          "state": 0,
          "color": "#299c46"
        }
      ],
      value_enabled=true,
    )
    .addTarget(
      prometheus.target(
        expr= prefix + '_cache_hit_rate{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"} > 0',
        legendFormat='{{cache_name}} - {{instance}}',
        instant=true,
      )
    )
  )

)


.addRow(
  row.new(title='Bloom Filters False Positive ratio')

  .addPanel(
    graphPanel.new(
      'Bloom Filter False Positive Ratio - Max per Table',
      description='Max of bloom filter false positive ratio, for each table',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, keyspace, table) (' + prefix + '_table_bloom_filter_false_ratio{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max BF false positive: {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Bloom Filter False Positive Ratio - Max per Node',
      description='Max of bloom filter false positive ratio, for each node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance) (' + prefix + '_table_bloom_filter_false_ratio{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max BF false positive: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Bloom Filter False Positive Ratio - Max per Table and Node',
      description='Max of bloom filter false positive ratio, for each combination of table and node',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      max=1,
      bars=false,
      lines=true,
      stack=false,
      decimals=2,
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster, dc, rack, instance, keyspace, table) (' + prefix + '_table_bloom_filter_false_ratio{keyspace!~"system|system_distributed|system_traces|system_auth|system_schema", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max BF false positive: {{keyspace}}.{{table}} - {{instance}}',
      )
    )
  )

)

.addRow(
  row.new(title='Hardware / Operating System')
 .addPanel(
    graphPanel.new(
      'CPU Utilization',
      description='CPU utilisation',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      percentage=true,
      decimals=1,
      min=0,
      max=1,
    )
    .addTarget(
      prometheus.target(
        expr='(1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))))',
        legendFormat='{{dc}}-{{instance}} ({{cluster}})',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Unix Load (1m rate)',
      description='Unix load for all nodes',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
    )
    .addTarget(
      prometheus.target(
        'collectd_load_shortterm{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}',
        legendFormat='{{dc}}-{{instance}} ({{cluster}})',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Context Switching',
      description='Context switching rate for all nodes',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
    )
    .addTarget(
      prometheus.target(
        'rate(collectd_contextswitch_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])',
        legendFormat='{{dc}}-{{instance}} ({{cluster}})',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Memory Utilisation',
      description='Maximum Memory allocated per usage (worst node) - excludes caches, buffers, etc',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      fill=1,
      linewidth=2,
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, memory) (collectd_memory{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='memory {{memory}} - {{dc}}-{{instance}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Disk Read Thoughput',
      description='Disk read throughput per node',
      format='bps',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
    )
    .addTarget(
      prometheus.target(
        expr='rate(collectd_processes_disk_octets_read_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])',
        legendFormat='{{dc}}-{{instance}} ({{cluster}})',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Network I/O',
      description='Network In and Out per node',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=1,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      bars=false,
    )
    .addTarget(
      prometheus.target(
        expr='rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])',
        legendFormat='outgoing {{dc}}-{{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='-1 * (rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='incoming {{dc}}-{{instance}}',
      )
    )
  )
)

.addRow(
  row.new(title='Disks Performances')

  .addPanel(
    graphPanel.new(
      'Disk Reads IOPS - Total per Node',
      description='Sum of all disks hits for reads per second, for each node',
      format='iops',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='Sum by (cluster, dc, rack, instance) (rate(collectd_processes_io_ops_read_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='disks reads - iops: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Disk Reads Throughput - Total per Node',
      description='Sum of all disks throughput for reads per second, for each node',
      format='bps',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      min=0,
      bars=false,
      lines=true,
      stack=false,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='Sum by (cluster, dc, rack, instance) (rate(collectd_processes_io_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='disks reads - throughput: {{instance}}',
      )
    )
  )

  // Disk Read Latency (io.r_await?)
  // Disk Read Queued (io.rrqm/s)
  // Disk Utilization (io.util%)

)

.addRow(
  row.new(title='JVM / Garbage Collection')
  .addPanel(
    graphPanel.new(
      'Application Throughput (% time NOT doing GC)',
      description='Percentage of the time the node is *not* doing a GC, thus Cassandra is not stopped for GC',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      decimals=2,
      max=1,
    )
    .addTarget(
      prometheus.target(
        expr='1 - (sum by (cluster, dc, rack, instance) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])) / 1000)',
        legendFormat='{{dc}}-{{instance}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Garbage Collection Time',
      description='Garbage collection duration',
      format='ms',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
    )
    .addTarget(
      prometheus.target(
        expr='rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])',
        legendFormat='{{dc}}-{{instance}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'JVM Heap Memory Utilisation',
      description='JVM Heap Memory size (worst node) and minimum available heap size per node',
      format='bytes',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=true,
      shared_tooltip=false,
      fill=1,
      linewidth=2,
    )
    .addTarget(
      prometheus.target(
        expr= prefix + '_jvm_memory_used{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}',
        legendFormat='{{dc}}-{{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='min by ( cluster)
        (' + prefix + '_jvm_memory_max{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Heap memory available',
      )
    )
  )
)
