{
  _config+:: {
    // mcacSelector is inserted as part of the label selector in
    // PromQL queries to identify metrics collected from Cassandra
    // servers.
    // With the Kubernetes cass-operator the following selector can be used
    // cassandraSelector: 'cassandra_datastax_com_cluster!="", cassandra_datastax_com_datacenter!=""',
    // TODO: use in all dashboard queries
    cassandraSelector: 'cluster!="", dc!=""',

    // dimensions is a way to help mixin users to add high level target grouping to their alerts and dashboards.
    // With the help of dimensions you can use a single observability stack to monitor several Cassandra clusters.
    // Each label of the list will be used as in the alerts to define aggregations (in `by ()`) and in the dashboards to define variables.
    // Inspired from: https://github.com/thanos-io/thanos/blob/v0.25.2/mixin/config.libsonnet
    // TODO: use in dashboards variable templates
    dimensions: ['cluster', 'dc'],

    // runbookURLPattern is used to create a `runbook_url` annotation for each alert
    runbookURLPattern: 'https://github.com/datastax/metric-collector-for-apache-cassandra/tree/master/mixin/runbook.md#alert-name-%s',
  },
}
