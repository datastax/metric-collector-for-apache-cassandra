local grafana = import 'github.com/grafana/grafonnet-lib/grafonnet/grafana.libsonnet';

local dashboard = grafana.dashboard;
local row = grafana.row;
local singlestat = grafana.singlestat;
local graphpanel = grafana.graphPanel;
local text = grafana.text;
local prometheus = grafana.prometheus;
local template = grafana.template;

local graphHeight = 300;
local singlestatHeight = 100;
local singlestatSpan = 1;
local graphSpan = 4;

{
  _config+:: {
    metricPrefix: error 'must provide metric prefix',
  },
  grafanaDashboards+:: {
    'cassandra-condensed.json':
      dashboard.new(
        'Cassandra Cluster Condensed',
        description='Single pane of glass for most important Cassandra metrics',
        schemaVersion=14,
        refresh='30s',
        time_from='now-30m',
        editable=true,
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
            cluster: 'Cluster',
            dc: 'Datacenter',
            rack: 'Rack',
            instance: 'Host',
          },
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
          allValues='.*',
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
          allValues='.*',
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
          allValues='.*',
        )
      )
      .addTemplate(
        template.new(
          'keyspace',
          '$PROMETHEUS_DS',
          'label_values(' + $._config.metricPrefix + '_table_read_latency_total{cluster=~"$cluster", dc=~"$dc"}, keyspace)',
          label='Keyspace',
          refresh='time',
          includeAll=true,
          allValues='.*',
        )
      )
      .addTemplate(
        template.new(
          'table',
          '$PROMETHEUS_DS',
          'label_values(' + $._config.metricPrefix + '_table_read_latency_total{cluster=~"$cluster", dc=~"$dc", keyspace=~"$keyspace"}, table)',
          label='Table',
          refresh='time',
          includeAll=true,
          allValues='.*',
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
          allValues='.*',
        )
      )
      .addTemplate(
        template.custom(
          'latency',
          '0.999,0.99,0.98,0.95,0.90,0.75,0.50',
          '0.95',
          valuelabels={
            '0.999': 'P999',
            '0.99': 'P99',
            '0.98': 'P98',
            '0.95': 'P95',
            '0.90': 'P90',
            '0.75': 'P75',
            '0.50': 'P50',
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
            description='Nodes that are currently running in this time window',
            format='none',
            decimals=0,
            datasource='$PROMETHEUS_DS',
            colorValue=true,
            colors=['#d44a3a', '#299c46', '#299c46'],
            thresholds='0.1,1000',
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'count(' + $._config.metricPrefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"} >= 0) or vector(0)'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'Nodes Down',
            description='Nodes that are currently not running in this time window',
            format='none',
            decimals=0,
            colorValue=true,
            colors=['#299c46', 'rgba(237, 129, 40, 0.89)', '#d44a3a'],
            datasource='$PROMETHEUS_DS',
            thresholds='1,2',
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'count(absent(sum(rate(' + $._config.metricPrefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[5m])))) OR vector(0)'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'Compactions / $rate',
            description='Rate of compactions during this window',
            format='none',
            decimals=0,
            datasource='$PROMETHEUS_DS',
            sparklineShow=true,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(rate(' + $._config.metricPrefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'CQL Requests / $rate',
            description='Rate of CQL requests during this window',
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
            description='Rate of Dropped requests during this window',
            format='none',
            datasource='$PROMETHEUS_DS',
            sparklineShow=true,
            thresholds='30,300',
            colorValue=true,
            decimals=0,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(irate(' + $._config.metricPrefix + '_table_dropped_mutations_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
            )
          )
        )
        .addPanel(
          text.new(
            transparent=true,
            mode='html',
            content='<a href="https://cassandra.apache.org" target="new"><img src="https://cassandra.apache.org/assets/img/logo-white.svg"/></a>',
            span=2
          )
        )
        .addPanel(
          singlestat.new(
            'CQL Clients',
            description='Number of connected clients during this time window',
            format='none',
            datasource='$PROMETHEUS_DS',
            sparklineShow=true,
            thresholds='100,1000',
            colorValue=true,
            decimals=0,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(' + $._config.metricPrefix + '_client_connected_native_clients{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"})'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'Timeouts / $rate',
            description='Client timeouts over the last $rate',
            format='none',
            datasource='$PROMETHEUS_DS',
            thresholds='100,300',
            colorValue=true,
            sparklineShow=true,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(irate(' + $._config.metricPrefix + '_client_request_timeouts_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))',
            )
          )
        )
        .addPanel(
          singlestat.new(
            'Hints / $rate',
            description='Hints stored over the last $rate',
            format='none',
            datasource='$PROMETHEUS_DS',
            thresholds='1000,30000',
            colorValue=true,
            sparklineShow=true,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(irate(' + $._config.metricPrefix + '_storage_hints_on_disk_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'Data Size',
            description='Data',
            format='bytes',
            datasource='$PROMETHEUS_DS',
            sparklineShow=true,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(' + $._config.metricPrefix + '_table_live_disk_space_used_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"})'
            )
          )
        )
        .addPanel(
          singlestat.new(
            'GC Time / $rate',
            description='Data',
            format='ms',
            decimals=1,
            datasource='$PROMETHEUS_DS',
            sparklineShow=true,
            span=singlestatSpan
          )
          .addTarget(
            prometheus.target(
              'sum(rate(' + $._config.metricPrefix + '_jvm_gc_time{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate]))'
            )
          )
        )
      )
      .addRow(
        row.new(
          title='Condensed Metrics',
          height=graphHeight
        )
        .addPanel(
          graphpanel.new(
            title='Requests Served / $by / $rate',
            description='(no keyspace/table filters apply)',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            labelY2='Clients Connected',
            legend_hideZero=true,
            legend_hideEmpty=true
          )
          .addSeriesOverride({
            alias: '/.*Connected/',
            yaxis: 2,
          })
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate])) by ($by, request_type)',
              legendFormat='{{$by}}:{{request_type}}'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(' + $._config.metricPrefix + '_client_connected_native_clients{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}) by ($by)',
              legendFormat='{{$by}}:Clients Connected'
            )
          )
        )
        .addPanel(
          graphpanel.new(
            title='Coordinator $latency Latency / $by',
            description='(no keyspace/table filters apply)',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            format='µs',
            min=0,
            legend_hideZero=true,
            legend_hideEmpty=true
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(rate(' + $._config.metricPrefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate])) by (le, request_type, $by))',
              legendFormat='$by:{{$by}} {{$latency}} {{request_type}}'
            )
          )
        )
        .addPanel(
          graphpanel.new(
            title='Memtable Space $keyspace.$table / $by',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            formatY1='bytes',
            formatY2='short',
            labelY2='Flush',
            min=0,
            legend_hideZero=true,
            legend_hideEmpty=true
          )
          .addSeriesOverride({
            alias: '/.*Flushes/',
            bars: true,
            lines: false,
            zindex: -3,
            yaxis: 2,
          })
          .addTarget(
            prometheus.target(
              expr='sum(' + $._config.metricPrefix + '_table_memtable_off_heap_size{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}) by ($by)',
              legendFormat='{{$by}} : Off Heap'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(' + $._config.metricPrefix + '_table_memtable_on_heap_size{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}) by ($by)',
              legendFormat='{{$by}} : On Heap'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(idelta(' + $._config.metricPrefix + '_table_memtable_switch_count_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{$by}} : Flushes'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(idelta(' + $._config.metricPrefix + '_table_pending_flushes_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{$by}} : Pending Flushes'
            )
          )
        )
        .addPanel(
          graphpanel.new(
            title='Compactions $keyspace.$table / $by',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            format='bps',
            formatY2='short',
            labelY2='Count',
            legend_hideZero=true,
            legend_hideEmpty=true,
            min=0
          )
          .addSeriesOverride({
            alias: '/.*Compactions/',
            bars: true,
            lines: false,
            zindex: -3,
            yaxis: 2,
          })
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_table_compaction_bytes_written_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{by}} : Bytes Compacted'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_table_pending_compactions{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{by}} : Pending Compactions'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_compaction_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{by}} : Completed Compactions'
            )
          )
        )
        .addPanel(
          graphpanel.new(
            title='Table $latency Latency / $by',
            description='',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            format='µs',
            min=0,
            legend_hideZero=true,
            legend_hideEmpty=true
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(irate(' + $._config.metricPrefix + '_table_range_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by (le, $by))',
              legendFormat='$by:{{$by}} Local Range Scan'
            )
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(irate(' + $._config.metricPrefix + '_table_read_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by (le, $by))',
              legendFormat='$by:{{$by}} Local Read'
            )
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(irate(' + $._config.metricPrefix + '_table_write_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by (le, $by))',
              legendFormat='$by:{{$by}} Local Write'
            )
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(irate(' + $._config.metricPrefix + '_table_coordinator_read_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table"}[$rate])) by (le, $by))',
              legendFormat='$by:{{$by}} Coordinator Read'
            )
          )
          .addTarget(
            prometheus.target(
              expr='histogram_quantile($latency, sum(irate(' + $._config.metricPrefix + '_table_coordinator_scan_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", keyspace=~"$keyspace", table=~"$table", instance=~"$host"}[$rate])) by (le, $by))',
              legendFormat='$by:{{$by}} Coordinator Range Scan'
            )
          )
        )
        .addPanel(
          graphpanel.new(
            title='Streaming / $by / $rate',
            description='',
            datasource='$PROMETHEUS_DS',
            span=graphSpan,
            format='Bps',
            min=0,
            legend_hideZero=true,
            legend_hideEmpty=true
          )
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_streaming_total_incoming_bytes_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{$by}}: Incoming Stream'
            )
          )
          .addTarget(
            prometheus.target(
              expr='sum(irate(' + $._config.metricPrefix + '_streaming_total_outgoing_bytes_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$host"}[$rate])) by ($by)',
              legendFormat='{{$by}}: Outgoing Stream'
            )
          )
        )
      ),
  },
}
