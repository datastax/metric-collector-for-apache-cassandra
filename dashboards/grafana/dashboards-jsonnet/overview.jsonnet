local grafana = (import 'grafonnet/grafana.libsonnet')
              + (import 'grafonnet-polystat-panel/plugin.libsonnet');
local dashboard = grafana.dashboard;
local prometheus = grafana.prometheus;
local template = grafana.template;
local row = grafana.row;

local graphPanel = grafana.graphPanel;
local tablePanel = grafana.tablePanel;
local singleStatPanel = grafana.singlestat;
local textPanel = grafana.text;
local polystatPanel = grafana.polystatPanel;

local prefix = std.extVar('prefix');

local fillLatencySeriesOverrides = {
    'alias': 'p999',
    'fillBelowTo': 'p98',
    'lines': false
};
local removeMinLatencySeriesOverrides = {
    'alias': 'p98',
    'lines': false
};

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
  'Cassandra Overview',
  schemaVersion=14,
  refresh='30s',
  time_from='now-30m',
  editable=true,
  tags=['Cassandra', 'Overview'],
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
    'node',
    '$PROMETHEUS_DS',
    'label_values(collectd_collectd_queue_length{cluster=~"$cluster", dc=~"$dc", rack=~"$rack"}, instance)',
    label='Node',
    refresh='time',
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
  row.new(title='Request Throughputs (Coordinator Perspective)')
  .addPanel(
    graphPanel.new(
      'Request Throughputs',
      description='Total Requests Per Cluster, by Request Type',
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
        expr='sum by (cluster, request_type) (rate(' + prefix + '_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='{{request_type}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Error throughputs',
      description='Total Timeouts, Failures, Unavailable Rates for each cluster',
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
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_failures_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} failures',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_timeouts_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} timeouts',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_unavailables_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} unavailable errors',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_unfinished_commit_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} unfinished commit errors',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_condition_not_met_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} condition not met errors',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_contention_histogram_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{request_type}} contention histogram errors',
      )
    )
  )
  .addPanel(
    singleStatPanel.new(
      'Read / Write Distribution',
      description='Part of reads in the total of standard requests (Reads+Writes). CAS, Views, ... operations are ignored.',
      format='percentunit',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      postfix=' Reads',
      postfixFontSize='30%',
      valueFontSize='30%',
      valueName="current",
      decimals=2,
      thresholds='0.25,0.5,0.75',
      timeFrom='',
      colors=[
        "#DEB6F2",
        "#CA95E5",
        "#8F3BB8"
      ],
      gaugeShow=true,
      gaugeMinValue=0,
      gaugeMaxValue=1,
      gaugeThresholdLabels=true,
      gaugeThresholdMarkers=false,
      sparklineFillColor='rgba(31, 118, 189, 0.18)',
      sparklineFull=false,
      sparklineLineColor='#FFB357',
      sparklineShow=false
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster, request_type) (irate(' + prefix + '_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="read"}[1m])) / ignoring (request_type) (sum by (cluster, request_type) (irate(' + prefix + '_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="read"}[1m])) + ignoring (request_type) sum by (cluster, request_type) (irate(' + prefix + '_client_request_latency_total{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="write"}[1m])))',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Read Latency (98 - 999th percentile)',
      description='Read latency for coordinated reads',
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
        expr='histogram_quantile(0.98, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="read"}[5m])) by (le, cluster))',
        legendFormat='p98',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="read"}[5m])) by (le, cluster))',
        legendFormat='p99',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="read"}[5m])) by (le, cluster))',
        legendFormat='p999',
      )
    )
    .addSeriesOverride(fillLatencySeriesOverrides)
    .addSeriesOverride(removeMinLatencySeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'Write Latency (98th - p999 Percentile)',
      description='Write latency for coordinated writes',
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
        expr='histogram_quantile(0.98, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="write"}[5m])) by (le, cluster))',
        legendFormat='p98',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.99, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="write"}[5m])) by (le, cluster))',
        legendFormat='p99',
      )
    )
    .addTarget(
      prometheus.target(
        expr='histogram_quantile(0.999, sum(irate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type="write"}[5m])) by (le, cluster))',
        legendFormat='p999',
      )
    )
    .addSeriesOverride(fillLatencySeriesOverrides)
    .addSeriesOverride(removeMinLatencySeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'Other Latencies',
      description='Other p99 latencies for coordinated requests',
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
        # In scope!~"Write|Read|.*-.*", we want to exclude charts above and all the per-consistency_level info like "Read-LOCAL_ONE"
        expr='histogram_quantile(0.99, sum(rate(' + prefix + '_client_request_latency_bucket{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", request_type!~"write|read|.*-.*"}[1m])) by (le, request_type, cluster))',
        legendFormat='p99 {{request_type}}'
      )
    )
  )
)
.addRow(
  row.new(title='Nodes Status',)
  .addPanel(
    polystatPanel.new(
      'Nodes Status',
      description='Nodes Status uses Internal/Gossip activity. Be mindful that if Native or Thrift protocol are disabled, the nodes won\'t be reachable, and still marked up',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      span=12,
      global_unit_format='none',
      global_operator_name='current',
      global_thresholds=[
        {
          "value": 0,
          "state": 2,
          "color": "#d44a3a"
        },
        {
          "value": 1,
          "state": 0,
          "color": "#299c46"
        }
      ],
      range_maps=[
        {
          "from": "0",
          "to": "0.9999",
          "text": "DOWN"
        },
        {
          "from": "1",
          "to": "1",
          "text": "UP"
        }
      ],
      mapping_type=2,
      value_enabled=true,
    )
    .addTarget(
      prometheus.target(
        'max by (cluster, dc, rack, instance) (changes(' + prefix + '_thread_pools_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", pool_name="gossip_stage"}[2m:30s])) > bool 0',
        legendFormat='{{instance}}',
        instant=true,
      )
    )
  )
  .addPanel(
    singleStatPanel.new(
      'Nodes Count',
      description='Nodes up and down in the cluster',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      decimals=0,
      prefix='Total:',
      postfix=' Nodes',
      postfixFontSize='80%',
      valueFontSize='80%',
      span=4
    )
    .addTarget(
      prometheus.target(
        expr='count by (cluster) (max by (cluster, dc, rack, instance) (collectd_collectd_queue_length{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}))',
        legendFormat='Total Number Of Nodes',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Nodes Status History',
      description='Nodes up and down in the cluster per protocol/activity',
      format='short',
      datasource='$PROMETHEUS_DS',
      transparent=true,
      decimals=0,
      fill=0,
      legend_show=true,
      legend_values=true,
      legend_current=true,
      legend_alignAsTable=true,
      legend_sort='current',
      legend_sortDesc=false,
      shared_tooltip=false,
      min=0,
      span=8
    )
    .addTarget(
      prometheus.target(
        expr='count by (cluster) (max by (cluster, dc, rack, instance) (collectd_collectd_queue_length{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}))',
        legendFormat='Total Number Of Nodes',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster) (max by (cluster, datacenter, rack, instance) (changes(' + prefix + '_thread_pools_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", pool_name="native"}[2m:30s])) > bool 0)',
        legendFormat='Nodes Coordinating Requests (Native protocol)',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster) (max by (cluster, datacenter, rack, instance) (changes(' + prefix + '_thread_pools_completed_tasks{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", pool_name="gossip_stage"}[2m:30s])) > bool 0)',
        legendFormat='Nodes With Internal Activity (Gossip protocol)',
      )
    )
  )
)
.addRow(
  row.new(title='Data Status')
  .addPanel(
    tablePanel.new(
      'Disk Space Usage',
      description='Disk space used ordered (fullest disks first)',
      datasource='$PROMETHEUS_DS',
      transform='timeseries_aggregations',
      transparent=true,
      styles=[
        {
          "alias": "Node --> Mounting Point",
          "colorMode": null,
          "colors": [
            "rgba(245, 54, 54, 0.9)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(50, 172, 45, 0.97)"
          ],
          "dateFormat": "YYYY-MM-DD HH:mm:ss",
          "decimals": 2,
          "mappingType": 1,
          "pattern": "Metric",
          "preserveFormat": true,
          "sanitize": true,
          "thresholds": [],
          "type": "string",
          "unit": "short"
        },
        {
          "alias": "% Disk Space Used",
          "colorMode": "row",
          "colors": [
            "rgba(50, 172, 45, 0.97)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(245, 54, 54, 0.9)"
          ],
          "dateFormat": "YYYY-MM-DD HH:mm:ss",
          "decimals": 2,
          "link": false,
          "mappingType": 1,
          "pattern": "Current",
          "thresholds": [
            "0.5",
            "0.75",
          ],
          "type": "number",
          "unit": "percentunit"
        }
      ],
      columns=[
        {
          "text": "Current",
          "value": "current"
        }
      ],
      sort={
        "col": 1,
        "desc": true
      }
    )
    .addTarget(
      prometheus.target(
        expr='min by (instance, df) (1-(collectd_df_df_complex{df!~".*lxcfs.*", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", type="free"}
        / ignoring (type) (collectd_df_df_complex{df!~".*lxcfs.*", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", type="used"}
          + ignoring (type) collectd_df_df_complex{df!~".*lxcfs.*", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", type="reserved"}
          + ignoring (type) collectd_df_df_complex{df!~".*lxcfs.*", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node", type="free"}))
        )',
        legendFormat='{{cluster}}-{{instance}} --> {{df}}',
        instant=true
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Cassandra cluster Data Size',
      description='Total sizes of the data on distinct nodes',
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
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster) (' + prefix + '_table_live_disk_space_used_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Live space - {{cluster}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='sum by (cluster) (' + prefix + '_table_total_disk_space_used_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Total space - {{cluster}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'SSTable Count',
      description='SSTable Count Max and Average per table',
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
        expr='max by (cluster, keyspace, table) (' + prefix + '_table_live_ss_table_count{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Table - {{keyspace}}.{{table}}',
      )
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster) (' + prefix + '_table_live_ss_table_count{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Max in cluster - {{cluster}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Pending Compactions',
      description='Maximum pending compactions on any node in the cluster',
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
        expr='max by (cluster) (' + prefix + '_table_pending_compactions{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster) (' + prefix + '_table_pending_compactions{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster) (' + prefix + '_table_pending_compactions{cluster=~"$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'Pending Compactions per Table',
      description='Maximum pending compactions per table',
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
      stack=true,
      decimals=0,
    )
    .addTarget(
      prometheus.target(
        expr='max by (cluster, keyspace, table) (' + prefix + '_table_pending_compactions{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max for {{keyspace}}.{{table}}',
      )
    )
  )
)
.addRow(
  row.new(title='Cassandra Internals')
  .addPanel(
    graphPanel.new(
      'Pending Tasks',
      description='Cluster wide pending threads, by thread pool name',
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
        expr='sum by (cluster, pool_name) (' + prefix + '_thread_pools_pending_tasks{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='{{cluster}} - pending {{pool_name}}',
      )
    )
  )
  .addPanel(
    graphPanel.new(
      'Blocked Tasks',
      description='Cluster wide blocked threads, by thread pool name',
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
        expr='sum by (cluster, pool_name) (irate(' + prefix + '_thread_pools_total_blocked_tasks_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{cluster}} - blocked {{pool_name}}',
      )
    )
  )
 .addPanel(
    graphPanel.new(
      'Dropped Messages',
      description='Dropped messages rate summed by message type and cluster',
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
        expr='sum by (cluster, message_type) (irate(' + prefix + '_dropped_message_dropped_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m]))',
        legendFormat='{{cluster}} - dropped {{message_type}}',
      )
    )
  )
  .addPanel(
     graphPanel.new(
       'Active Tasks',
       description='active threads summed per cluster',
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
         expr='sum by (cluster, pool_name) (' + prefix + '_thread_pools_active_tasks{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
         legendFormat='{{cluster}} - active {{pool_name}}',
       )
     )
   )
  .addPanel(
     graphPanel.new(
       'Hinted Handoff',
       description='Sum of hints being handed off per cluster.',
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
         expr='sum by (cluster) (' + prefix + '_storage_total_hints_in_progress_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
         legendFormat='count',
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
        expr='max by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        expr='min by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        expr='avg by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{type="idle", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[1m])) / sum by (cluster, dc, rack, instance) (rate(collectd_cpu_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))))',
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
      'Disk Read Thoughput',
      description='Disk read throughput',
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
        expr='max by (cluster) (rate(collectd_processes_disk_octets_read_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster) (rate(collectd_processes_disk_octets_read_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster) (rate(collectd_processes_disk_octets_read_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'Disk Write Thoughput',
      description='Disk write throughput',
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
        expr='max by (cluster) (rate(collectd_processes_disk_octets_write_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster) (rate(collectd_processes_disk_octets_write_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster) (rate(collectd_processes_disk_octets_write_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
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
        'sum by (cluster) (rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='outgoing',
      )
    )
    .addTarget(
      prometheus.target(
        'sum by (cluster) (rate(collectd_interface_if_octets_rx_total{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='incoming',
      )
    )
    .addSeriesOverride({
          "alias": "incoming",
          "transform": "negative-Y"
    })

  )
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
      min=0,
      max=1,
    )
    .addTarget(
      prometheus.target(
        'max by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m])) / 1000))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m])) / 1000))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster) (1 - (sum by (cluster, dc, rack, instance) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m])) / 1000))',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
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
        'max by (cluster) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster) (rate(' + prefix + '_jvm_gc_time{cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"}[5m:1m]))',
        legendFormat='avg',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
  )
  .addPanel(
    graphPanel.new(
      'JVM Heap Memory Utilisation',
      description='Maximum JVM Heap Memory size (worst node) and minimum available heap size',
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
        'max by (cluster)
        (' + prefix + '_jvm_memory_used{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='max',
      )
    )
    .addTarget(
      prometheus.target(
        'min by (cluster)
        (' + prefix + '_jvm_memory_used{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='min',
      )
    )
    .addTarget(
      prometheus.target(
        'avg by (cluster)
        (' + prefix + '_jvm_memory_used{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='avg',
      )
    )
    .addTarget(
      prometheus.target(
        'min by ( cluster)
        (' + prefix + '_jvm_memory_max{memory_type="heap", cluster="$cluster", dc=~"$dc", rack=~"$rack", instance=~"$node"})',
        legendFormat='Heap memory available',
      )
    )
    .addSeriesOverride(fillMinMaxSeriesOverrides)
    .addSeriesOverride(removeMinlineSeriesOverrides)
  )
)
