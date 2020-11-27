local grafana = (import 'grafonnet/grafana.libsonnet');
local dashboard = grafana.dashboard;
local prometheus = grafana.prometheus;
local template = grafana.template;
local row = grafana.row;

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
  'Cassandra Write Path',
  schemaVersion=14,
  refresh='30s',
  time_from='now-30m',
  editable=true,
  tags=['Cassandra', 'Write', 'Write-Path', 'Mutation', 'Insert', 'Update', 'Upsert'],
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
  row.new(title='Local Writes Throughputs (Node Perspective)')
  .addPanel(
    graphPanel.new(
      'Local Writes Throughput per Table',
      description='Total writes, cas_propose and cas_commit per table',
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
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_write_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Writes: {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_cas_propose_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Propose: {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, keyspace, table) (rate(' + prefix + '_table_cas_commit_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Commit: {{keyspace}}.{{table}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Local Writes Throughput per Node',
      description='Total writes, cas_propose and cas_commit per node',
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
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_write_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Writes: {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_cas_propose_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Propose: {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance) (rate(' + prefix + '_table_cas_commit_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Commit: {{instance}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Local Writes Throughput per Node and Table',
      description='Total writes, cas_propose and cas_commit per node and table',
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
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_write_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Writes: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_cas_propose_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Propose: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, dc, rack, instance, keyspace, table) (rate(' + prefix + '_table_cas_commit_latency_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='Cas Commit: {{keyspace}}.{{table}} on {{instance}}',
      )
    )
  )
)


.addRow(
  row.new(title='Local Write Latencies')

  .addPanel(
    graphPanel.new(
      'Local Write Latency per Table',
      description='Write latency for local writes per table (98 - 999th percentile)',
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
        expr='histogram_quantile(0.98, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p999 - {{keyspace}}.{{table}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Local Write Latency per Node',
      description='Write latency for local writes per node (98 - 999th percentile)',
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
        expr='histogram_quantile(0.98, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, dc, rack, instance, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p999 - {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Local Write Latency per Table and Node',
      description='Write latency for local writes per table and per node  (98 - 999th percentile)',
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
        expr='histogram_quantile(0.98, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p98 - {{keyspace}}.{{table}} - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
        legendFormat='p99 - {{keyspace}}.{{table}} - {{instance}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum by (cluster, dc, rack, instance, keyspace, table, le) (rate(' + prefix + '_table_write_latency_bucket{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])))',
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
      description='Pending threads per node, by thread pool name filtering threads possibly impacting writes',
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
        expr='sum by (cluster, dc, rack, instance, pool_name) (' + prefix + '_thread_pools_pending_tasks{pool_name=~"memtable_flush_writer|memtable_post_flush|migration_stage|counter_mutation_stage|mutation_stage|view_mutation_stage|misc_stage|secondary_index_management|hints_dispatcher", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{instance}} - pending {{pool_name}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Blocked Tasks per Node',
      description='Pending threads per node, by thread pool name filtering threads possibly impacting writes',
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
        expr='sum by (cluster, dc, rack, instance, pool_name) (' + prefix + '_thread_pools_total_blocked_tasks_total{pool_name=~"memtable_flush_writer|memtable_post_flush|migration_stage|counter_mutation_stage|mutation_stage|view_mutation_stage|misc_stage|secondary_index_management|hints_dispatcher", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{instance}} - blocked {{pool_name}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Dropped Messages per Node',
      description='Pending threads per node, by thread pool name filtering threads possibly impacting writes',
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
        expr='sum by (cluster, dc, rack, instance, message_type) (rate(' + prefix + '_dropped_message_dropped_total{message_type=~"_trace|batch_store|batch_remove|counter_mutation|hint|mutation", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{instance}} - dropped {{message_type}}',
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
  row.new(title='Hardware / Operating System')

 .addPanel(
    graphPanel.new(
      'CPU Utilization',
      description='Maximum CPU utilisation (max 100%)',
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
        expr='max by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))))',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'Unix Load (1m rate)',
      description='Max Unix load on a node for a cluster',
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
        expr='max by (cluster) (collectd_load_shortterm{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster) (collectd_load_shortterm{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster) (collectd_load_shortterm{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
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
        expr='min by (cluster) (sum by (cluster, dc, rack, instance) (collectd_memory{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}))',
        legendFormat='min memory available',
      )
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, memory) (sum by (cluster, dc, rack, instance, memory) (collectd_memory{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}))',
        legendFormat='max memory {{memory}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Network I/O',
      description='Network In and Out per cluster',
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
        'sum by (cluster) (rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='outgoing',
      )
    )
    .addTarget(
      prometheus.target(
        'sum by (cluster) (rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='incoming',
      )
    )
    .addSeriesOverride({
          "alias": "incoming",
          "transform": "negative-Y"
    })
  )
  .addPanel(
    graphPanel.new(
      'Context Switching',
      description='Amount of context switching per second per host',
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
        expr='sum by (cluster, dc, rack, instance) (rate(collectd_contextswitch_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='{{instance}} - Context Switches',
      )
    )
  )

)

.addRow(
  row.new(title='Disks Performances')

  // TODO Alain
  .addPanel(
    graphPanel.new(
      'Disk Writes IOPS - Total per Node',
      description='Sum of all disks hits for writes per second, for each node',
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
        expr='Sum by (cluster, dc, rack, instance) (rate(collectd_processes_io_ops_write_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='disks writes - iops: {{instance}}',
      )
    )
  )

  .addPanel(
    graphPanel.new(
      'Disk Writes Throughput - Total per Node',
      description='Sum of all disks throughputs for writes per second, for each node',
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
        expr='Sum by (cluster, dc, rack, instance) (rate(collectd_processes_io_octets_tx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m:30s]))',
        legendFormat='disks writes - throughput: {{instance}}',
      )
    )
  )

  // TODO
  // Disk Write Latency (io.w_await?)
  // Disk Write Queued (io.wrqm/s)
  // Disk Utilization (io.util%)
  // About disks, change to see disks individually (no aggregation) to see commitlog disk apart.

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


// TODO following versions:

// Cassandra
// Section with:
//    - memtable sizes
//    - mcac_table_waiting_on_free_memtable_space
//    - other memtable useful info (memtable switch count, ...)?

// add to latency section a chart specifically with CAS and MV latencies (specifically - apart from write latencies)?
// For now it's mixed with other latencies

// section time skew
// mcac_table_col_update_time_delta_histogram --> https://stackoverflow.com/questions/42180358/what-does-this-cassandra-metric-colupdatetimedeltahistogram-mean
// Clock drift (here and in overview dashboard as well!). Missing ntp data to build that chart
