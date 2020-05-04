local grafana = import 'grafonnet/grafana.libsonnet';

local dashboard = grafana.dashboard;
local row = grafana.row;
local singlestat = grafana.singlestat;
local graphpanel = grafana.graphPanel;
local text = grafana.text;
local prometheus = grafana.prometheus;
local template = grafana.template;

local prefix = std.extVar('prefix');

local textstatHeight = 100;
local graphHeight = 250;
local singlestatHeight = 125;
local singlestatSpan = 2;
local graphSpan = 6;

dashboard.new(
  'System & Node Metrics',
  description='Operating System Metrics and Apache Cassandra Node Information',
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
  template.interval(
    'rate',
    '1m,5m,10m,30m,1h,6h,12h,1d,7d,14d,30d',
    '5m',
    label='Rate',
  )
)
.addTemplate(
  template.new(
    'host',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{}, instance)',
    label='Host',
    refresh='time',
  )
)
.addPanel(
  text.new(
      transparent=true,
      mode="html",
      content='<a href="https://cassandra.apache.org" target="new"><img src="https://cassandra.apache.org/img/cassandra_logo.png"/></a>'
  ),
  {
    "h": 3,
    "w": 5,
    "x": 9,
    "y": -1
  },
)
.addRow(
  row.new(
    title='Basic CPU / Mem / Disk Gauge',
    height=singlestatHeight,
  )
  .addPanel(
    singlestat.new(
      'CPU Busy',
      description="Busy state of all CPU cores together",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='85,95',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        "(1 - ((sum(irate(collectd_cpu_total{instance='$host', type='idle'}[$rate])) by (instance) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) by (instance)))) * 100",
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Memory Used',
      description="Percentage of memory used (ignoring page cache)",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='85,95',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          '100 * ((sum(collectd_memory{instance="$host", memory="free"}) + sum(collectd_memory{instance="$host", memory="cached"}) + sum(collectd_memory{instance="$host", memory="buffered"})) / sum(collectd_memory{instance="$host"}))',
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Swap Used',
      description="Percentage of swap in use",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='0,1',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          "(sum(collectd_swap{instance='$host',swap='used'}) / sum(collectd_swap{instance='$host'})) * 100"
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Disk Used',
      description="Percentage of root disk in use",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='50,85',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        '(sum(collectd_df_df_complex{instance="$host", df="root", type="used"}) / sum(collectd_df_df_complex{instance="$host", df="root"})) * 100'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'CPU System Load (1m avg)',
      description="Busy state of all CPU cores together (1 min average)",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='85,95',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'avg(collectd_load_shortterm{instance="$host"}) / count(count(collectd_cpu_total{instance="$host"}) by (cpu)) * 100',
      )
    )
  )
  .addPanel(
    singlestat.new(
      'CPU System Load (5m avg)',
      description="Busy state of all CPU cores together (5 min average)",
      format='percent',
      datasource='$PROMETHEUS_DS',
      thresholds='85,95',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
        'avg(collectd_load_midterm{instance="$host"}) / count(count(collectd_cpu_total{instance="$host"}) by (cpu)) * 100',
      )
    )
  )
)
.addRow(
  row.new(
    title='Basic CPU / Mem / Disk Info',
    height=textstatHeight
  )
  .addPanel(
    singlestat.new(
      'CPU Cores',
      description="Total number of CPU cores",
      format="short",
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'count(count(collectd_cpu_total{instance="$host"}) by (cpu))'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Total RAM',
      description="Total amount of system memory",
      format="bytes",
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(collectd_memory{instance="$host"})'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Total Swap',
      description="Total amount of swap space",
      format="bytes",
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(collectd_swap{instance="$host"})'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Total RootFS',
      description="Total amount of disk space",
      format='bytes',
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'sum(collectd_df_df_complex{df="root",instance="$host"})'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'System Load (1m avg)',
      description="System Load (1m avg)",
      format="short",
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'collectd_load_shortterm{instance="$host"}'
      )
    )
  )
  .addPanel(
    singlestat.new(
      'System Uptime',
      description="Uptime of the host",
      format="s",
      decimals=1,
      datasource='$PROMETHEUS_DS',
      span=singlestatSpan
    )
    .addTarget(
      prometheus.target(
          'collectd_uptime{instance="$host"}'
      )
    )
  )
)
.addRow(
  row.new(
    title='Basic CPU / Mem Graph',
    height=graphHeight
  )
  .addPanel(
    graphpanel.new(
      title="CPU Basic",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      percentage=true,
      stack=true,
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type='system'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Busy System"
      )
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type='user'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Busy User"
      )
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type='wait'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Busy IOWait"
      )
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type='softirq'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Busy IRQ"
      )
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type!='idle',type!='system',type!='user',type!='wait',type!='softirq'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Busy Other"
      )
    )
    .addTarget(
      prometheus.target(
          expr="sum(irate(collectd_cpu_total{instance='$host',type='idle'}[$rate])) / sum(irate(collectd_cpu_total{instance='$host'}[$rate])) * 100",
          legendFormat="Idle"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="Basic memory usage",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="bytes",
      min=0
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_memory{instance="$host"})',
          legendFormat="RAM Total"
      )
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_memory{instance="$host"}) - sum(collectd_memory{instance="$host", memory="free"}) - sum(collectd_memory{instance="$host", memory="cached"}) - sum(collectd_memory{instance="$host", memory="buffered"})',
          legendFormat="RAM Used"
      )
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_memory{instance="$host", memory="cached"}) + sum(collectd_memory{instance="$host", memory="buffered"})',
          legendFormat="RAM Cache + Buffer"
      )
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_memory{instance="$host", memory="free"})',
          legendFormat="RAM Free"
      )
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_swap{instance="$host"}) - sum(collectd_swap{instance="$host", swap="free"})',
          legendFormat="SWAP Used"
      )
    )
  )
)
.addRow(
  row.new(
    title='Basic Network / Disk Graph',
    height=graphHeight
  )
  .addPanel(
    graphpanel.new(
      title="Network Traffic / Second",
      description="Basic network info per interface",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="bps",
      labelY1="Receive (-) /  Send (+)",

    )
    .addSeriesOverride({
          "alias": "/.*receive.*/",
          "transform": "negative-Y"
        })
    .addTarget(
      prometheus.target(
          expr="irate(collectd_interface_if_octets_rx_total{instance='$host'}[$rate]) * 8",
          legendFormat="{{interface}} receive"
      )
    )
    .addTarget(
      prometheus.target(
          expr="irate(collectd_interface_if_octets_tx_total{instance='$host'}[$rate]) * 8",
          legendFormat="{{interface}} send"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Network Packets / Second",
      description="Basic network info per interface",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="pps",
      labelY1="Receive (-) /  Send (+)",

    )
    .addSeriesOverride({
          "alias": "/.*receive.*/",
          "transform": "negative-Y"
        })
    .addTarget(
      prometheus.target(
          expr="irate(collectd_interface_if_packets_rx_total{instance='$host'}[$rate]) * 8",
          legendFormat="{{interface}} receive"
      )
    )
    .addTarget(
      prometheus.target(
          expr="irate(collectd_interface_if_packets_tx_total{instance='$host'}[$rate]) * 8",
          legendFormat="{{interface}} send"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Disk Activity / Second",
      description="Disk Activity / Second",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="Bps",
      labelY1="Read (-) /  Write (+)",
      legend_hideZero=true,
      legend_hideEmpty=true
    )
    .addSeriesOverride({
          "alias": "/.*Read.*/",
          "transform": "negative-Y"
        })
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_octets_read_total{instance="$host", disk=~".*\\\\d+"}[$rate])',
          legendFormat="{{disk}} - Read"
      )
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_octets_write_total{instance="$host", disk=~".*\\\\d+"}[$rate])',
          legendFormat="{{disk}} - Write"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Disk IOPS",
      description="Disk iops per disk",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="iops",
      labelY1="Read (-) /  Write (+)",
      legend_hideZero=true,
      legend_hideEmpty=true
    )
    .addSeriesOverride({
          "alias": "/.*Read.*/",
          "transform": "negative-Y"
        })
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_ops_read_total{instance="$host", disk=~".*\\\\d+"}[$rate])',
          legendFormat="{{disk}} - Read"
      )
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_ops_write_total{instance="$host", disk=~".*\\\\d+"}[$rate])',
          legendFormat="{{disk}} - Write"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Disk Used",
      description="Disk space used",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="decbytes",
      legend_hideZero=true,
      legend_hideEmpty=true
    )
    .addTarget(
      prometheus.target(
          expr='sum(collectd_df_df_complex{instance="$host", type="used"}) by (df)',
          legendFormat="{{df}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Disk Queue Length",
      description="The amount of requests pending in the disk queue",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideZero=true,
      legend_hideEmpty=true
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_io_time_weighted_io_time_total{instance="$host",disk=~".*[0-9]+"}[$rate]) / 1000',
          legendFormat="{{disk}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Disk Latency",
      description="Disk access times",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="ms",
      labelY1="Read (-) /  Write (+)",
      legend_hideZero=true,
      legend_hideEmpty=true
    )
    .addSeriesOverride({
          "alias": "/.*Read.*/",
          "transform": "negative-Y"
        })
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_time_read_total{instance="$host",disk=~".*[0-9]+"}[$rate])',
          legendFormat="{{disk}} - Read"
      )
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_disk_disk_time_write_total{instance="$host",disk=~".*[0-9]+"}[$rate])',
          legendFormat="{{disk}} - Write"
      )
    )
  )
)
.addRow(
  row.new(
    title='CPU Details',
    height=graphHeight,
    collapse=true
  )
  .addPanel(
    graphpanel.new(
      title="CPU User",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="percent",
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr='sum(rate(collectd_cpu_total{instance="$host", type="user"}[$rate])) by (cpu) / sum(rate(collectd_cpu_total{instance="$host"}[$rate])) by (cpu) * 100',
          legendFormat="{{cpu}}"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="CPU System",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="percent",
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr='sum(rate(collectd_cpu_total{instance="$host", type="system"}[$rate])) by (cpu) / sum(rate(collectd_cpu_total{instance="$host"}[$rate])) by (cpu) * 100',
          legendFormat="{{cpu}}"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="CPU IOWait",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="percent",
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr='sum(rate(collectd_cpu_total{instance="$host", type="wait"}[$rate])) by (cpu) / sum(rate(collectd_cpu_total{instance="$host"}[$rate])) by (cpu) * 100',
          legendFormat="{{cpu}}"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="CPU SoftIRQ",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="percent",
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr='sum(rate(collectd_cpu_total{instance="$host", type="softirq"}[$rate])) by (cpu) / sum(rate(collectd_cpu_total{instance="$host"}[$rate])) by (cpu) * 100',
          legendFormat="{{cpu}}"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="CPU Other",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="percent",
      min=0,
      max=100
    )
    .addTarget(
      prometheus.target(
          expr='sum(rate(collectd_cpu_total{instance="$host", type=~"(interrupt|nice|steal)"}[$rate])) by (cpu, type)  / ignoring(type) group_left sum(rate(collectd_cpu_total{instance="$host" }[$rate])) by (cpu) * 100',
          legendFormat="{{cpu}} - {{type}}"
      )
    )
  )
)
.addRow(
  row.new(
    title='Advanced Details',
    height=graphHeight,
    collapse=true
  )
  .addPanel(
    graphpanel.new(
      title="Context Switches / Second",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short"
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_contextswitch_total{instance="$host"}[$rate])',
          legendFormat="Context Switches"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="IRQ Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_irq_total{instance="$host", irq != "LOC"}[$rate])',
          legendFormat="{{irq}}"
      )
    )
  )
   .addPanel(
    graphpanel.new(
      title="NUMA Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='irate(collectd_numa_vmpage_action_total{instance="$host"}[$rate])',
          legendFormat="{{numa}} - {{type}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="TCP Connection Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='collectd_tcpconns_tcp_connections{instance="$host"}',
          legendFormat="{{tcpconns}} - {{type}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="TCP Connection Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_protocols_protocol_counter_total{instance="$host"}[$rate])',
          legendFormat="{{protocols}} - {{type}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Processor Speeds",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="hertz",
      points=true,
      lines=false,
      pointradius=5,
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='collectd_cpufreq{instance="$host"}',
          legendFormat="{{cpufreq}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Page Cache Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_vmem_vmpage_faults_majflt_total{instance="$host"}[$rate])',
          legendFormat="Major fault"
      )
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_vmem_vmpage_faults_minflt_total{instance="$host"}[$rate])',
          legendFormat="Minor fault"
      )
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_vmem_vmpage_action_total{instance="$host"}[$rate])',
          legendFormat="Action - {{vmem}}"
      )
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_vmem_vmpage_io_in_total{instance="$host"}[$rate])',
          legendFormat="IO read page"
      )
    )
    .addTarget(
      prometheus.target(
          expr='rate(collectd_vmem_vmpage_io_out_total{instance="$host"}[$rate])',
          legendFormat="IO write page"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Page Cache Layout",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      percentage=true,
      stack=true,
      min=0,
      max=100,
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='collectd_vmem_vmpage_number{instance="$host"}',
          legendFormat="{{vmem}}"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="Process Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="short",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='collectd_processes_ps_count_threads{instance="$host"}',
          legendFormat="Thread Count"
      )
    )
    .addTarget(
      prometheus.target(
          expr='collectd_processes_ps_count_processes{instance="$host"}',
          legendFormat="Process Count"
      )
    )
    .addTarget(
      prometheus.target(
          expr='collectd_processes_ps_state{instance="$host"}',
          legendFormat="Process State - {{processes}}"
      )
    )
  )
)
.addRow(
  row.new(
    title='Basic Cassandra Overview',
    height=singlestatHeight
  )
  .addPanel(
    singlestat.new(
      'SSTable Count',
      description="Number of sstables on the node",
      format='short',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan,
      thresholds="100000,500000"
    )
    .addTarget(
      prometheus.target(
        "sum(" + prefix + "_table_live_ss_table_count{instance='$host'})"
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Pending Compactions',
      description="Number of pending compactions on the node",
      format='short',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan,
      thresholds="10,50"
    )
    .addTarget(
      prometheus.target(
        "sum(" + prefix + "_compaction_pending_tasks{instance='$host'})"
      )
    )
  )
  .addPanel(
    singlestat.new(
      'Connected Clients',
      description="Number of client connections to the node",
      format='percent',
      datasource='$PROMETHEUS_DS',
      sparklineShow=true,
      gaugeShow=true,
      span=singlestatSpan,
      thresholds="100,1000"
    )
    .addTarget(
      prometheus.target(
        "sum(" + prefix + "_client_connected_native_clients{instance='$host'})"
      )
    )
  )
  .addPanel(
    graphpanel.new(
      title="GC Activity",
      datasource='$PROMETHEUS_DS',
      span=graphSpan,
      format="bytes",
      legend_hideEmpty=true,
      legend_hideZero=true
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_jvm_memory_max{instance="$host", memory_type="total"})',
          legendFormat="JVM Heap Total"
      )
    )
    .addSeriesOverride({
          "alias": "/.*Total.*/",
          "fill": 0
    })
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_jvm_memory_used{instance="$host", memory_type="non_heap"})',
          legendFormat="JVM Non-Heap Used"
      )
    )
    .addTarget(
      prometheus.target(
          expr='sum(' + prefix + '_jvm_memory_used{instance="$host", memory_type="heap"})',
          legendFormat="JVM Heap Used"
      )
    )
  )
)