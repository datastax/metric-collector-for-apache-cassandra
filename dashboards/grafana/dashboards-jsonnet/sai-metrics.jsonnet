local grafana = import 'grafonnet/grafana.libsonnet';

local dashboard = grafana.dashboard;
local row = grafana.row;
local singlestat = grafana.singlestat;
local graphpanel = grafana.graphPanel;
local text = grafana.text;
local prometheus = grafana.prometheus;
local template = grafana.template;

local prefix = std.extVar('prefix');

local graphHeight = 300;
local singlestatHeight = 100;
local singlestatSpan = 1;
local graphSpan = 4;

dashboard.new(
  'Storage Attached Index',
  description='Single pane of glass for most important Storage Attached Index (SAI) metrics',
  editable=true,
  schemaVersion=14,
  time_from='now-30m',
  refresh='1m',
  tags=['os'],
  style='dark'
)
.addTemplate(
  template.datasource(
    'PROMETHEUS_DS',
    'prometheus',
    'Prometheus',
    hide='all',
  )
)

.addTemplate(
  template.custom(
    'by',
    'cluster,dc,rack,instance',
    'cluster',
    valuelabels={
      "cluster": "Cluster",
      "dc" : "Datacenter",
      "rack" : "Rack",
      "instance" : "Host"},
    label='Group By',
  )
)
.addTemplate(
  template.interval(
    'rate',
    '1m,5m,10m,30m,1h,6h,12h,1d,7d,14d,30d',
    '5m',
    label='Rate',
  )
)
.addTemplate(
  template.new(
    'cluster',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{}, cluster)',
    label='Cluster',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'dc',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster=~"$cluster"}, dc)',
    label='DataCenter',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'rack',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster=~"$cluster", dc=~"$dc"}, rack)',
    label='Rack',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'keyspace',
    '$PROMETHEUS_DS',
    'label_values(' + prefix + '_table_read_latency_total{cluster=~"$cluster", dc=~"$dc"}, keyspace)',
    label='Keyspace',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'table',
    '$PROMETHEUS_DS',
    'label_values(' + prefix + '_table_read_latency_total{cluster=~"$cluster", dc=~"$dc", keyspace=~"$keyspace"}, table)',
    label='Table',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.new(
    'host',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster=~"$cluster", dc=~"$dc", rack=~"$rack"}, instance)',
    label='Host',
    refresh='time',
    includeAll=true,
    allValues=".*",
  )
)
.addTemplate(
  template.custom(
    'latency',
    '0.999,0.99,0.98,0.95,0.90,0.75,0.50',
    '0.95',
    valuelabels={
      "0.999" : "P999",
      "0.99"  : "P99",
      "0.98"  : "P98",
      "0.95"  : "P95",
      "0.90"  : "P90",
      "0.75"  : "P75",
      "0.50"  : "P50"
    },
    label='Percentile'
  )
)
.addRow(
  row.new(
    title='Cluster Overview',
    height=singlestatHeight,
  )
  .addPanel(
    singlestat.new(
      'Nodes Up',
      description="Nodes that are currently running in this time window",
      format='none',
      decimals=0,
      datasource='$PROMETHEUS_DS',
      colorValue=true,
      colors=["#d44a3a", "#299c46", "#299c46"],
      thresholds='0.1,1000',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'count(' + prefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"} >= 0) or vector(0)'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Nodes Down',
      description="Nodes that are currently not running in this time window",
      format='none',
      decimals=0,
      colorValue=true,
      colors=[ "#299c46", "rgba(237, 129, 40, 0.89)", "#d44a3a"],
      datasource='$PROMETHEUS_DS',
      thresholds='1,2',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'count(absent(sum(rate(' + prefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[5m])))) OR vector(0)'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Compactions / $rate',
      description="Rate of compactions during this window",
      format='none',
      decimals=0,
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'sum(rate(' + prefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'CQL Requests / $rate',
      description="Rate of CQL requests during this window",
      format='none',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      decimals=0,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'sum(irate(dse_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Dropped Messages / $rate',
      description="Rate of Dropped requests during this window",
      format='none',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      thresholds="30,300",
      colorValue=true,
      decimals=0,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(irate(' + prefix + '_table_dropped_mutations_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
      )
    )
  )
  .addPanel(
    text.new(
      transparent=true,
      mode="html",
      content='<a href="https://cassandra.apache.org" target="new"><img src="https://cassandra.apache.org/img/cassandra_logo.png"/></a>',
      span=2
    )
  )
  .addPanel(
    singlestat.new(
      'CQL Clients',
      description="Number of connected clients during this time window",
      format='none',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      thresholds="100,1000",
      colorValue=true,
      decimals=0,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(' + prefix + '_client_connected_native_clients{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"})'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Timeouts / $rate',
      description="Client timeouts over the last $rate",
      format='none',
      datasource='$PROMETHEUS_DS',
      thresholds='100,300',
      colorValue=true,
      sparklineShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'sum(irate(' + prefix + '_client_request_timeouts_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))',
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Hints / $rate',
      description="Hints stored over the last $rate",
      format='none',
      datasource='$PROMETHEUS_DS',
      thresholds='1000,30000',
      colorValue=true,
      sparklineShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'sum(irate(' + prefix + '_storage_hints_on_disk_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Data Size',
      description="Data",
      format='bytes',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(' + prefix + '_table_live_disk_space_used_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"})'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'GC Time / $rate',
      description="Data",
      format='ms',
      decimals=1,
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(rate(' + prefix + '_jvm_gc_time{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
      )
    )
  )
)
.addRow(
  row.new(
    title='Cluster Index Metrics',
    height=graphHeight
  )
  .addPanel(
    graphpanel.new(
      title="Index Count",
      description="Global count of Storage Atttached Indexes",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_index_count{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
          legendFormat="{{$by}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Queryable Count",
      description="Global count of Storage Atttached Indexes that are queryable",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_queryable_index_count{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
          legendFormat="{{$by}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Building Count",
      description="Global count of Storage Atttached Indexes that are building",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_index_builds_in_progress{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
          legendFormat="{{$by}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Disk Used Bytes",
      description="Global view of SAI disk used in bytes",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      formatY1="bytes",
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_disk_used_bytes{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
          legendFormat="{{$by}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Memtable Bytes",
      description="Global view of SAI memtable bytes used",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      formatY1="bytes",
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_index_metrics_memtable_index_bytes{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
          legendFormat="{{$by}}"
      )
    )
  )
)
.addRow(
  row.new(
    title='Table Index Metrics',
    height=graphHeight
  )
  .addPanel(
    graphpanel.new(
      title="Index Count",
      description="Table-level count of Storage Atttached Indexes",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_index_count{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Queryable Count",
      description="Table-level count of Storage Atttached Indexes that are queryable",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_queryable_index_count{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Building Count",
      description="Table-level count of Storage Atttached Indexes that are building",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      decimals=0,
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_total_index_builds_in_progress{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Disk Used Bytes",
      description="Table-level view of SAI disk used in bytes",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      formatY1="bytes",
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_disk_used_bytes{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Disk Usage Percentage of Base Table",
      description="Table-level view of SAI disk usage as percentage of base table size",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      formatY1="percentunit",
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_table_state_metrics_disk_percentage_of_base_table{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Index Memtable Bytes",
      description="Table-level view of SAI memtable bytes used",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      formatY1="bytes",
      min=0,
      legend_hideZero=false,
      legend_hideEmpty=false
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_storage_attached_index_index_metrics_memtable_index_bytes{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host", table=~"$table"}) by (table, $by)',
          legendFormat="{{$by}} : {{table}}"
      )
    )
  )
)