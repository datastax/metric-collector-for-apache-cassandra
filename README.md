Metric Collector for Apache Cassandra&reg; (MCAC)
=================================================

Metric collection and Dashboards for Apache Cassandra (2.2, 3.0, 3.11, 4.0) clusters.

![Testing](https://github.com/datastax/metric-collector-for-apache-cassandra/workflows/Testing/badge.svg)
![Release](https://github.com/datastax/metric-collector-for-apache-cassandra/workflows/Release/badge.svg)

## Introduction

   Metric Collector for Apache Cassandra (MCAC) aggregates OS and C* metrics along with diagnostic events
   to facilitate problem resolution and remediation. 
   It supports existing Apache Cassandra clusters and is a self contained drop in agent.

   * Built on [collectd](https://collectd.org), a popular, well-supported, open source metric collection agent. 
   With over 90 plugins, you can tailor the solution to collect metrics most important to you and ship them to 
   wherever you need. 
   
   * Easily added to Cassandra nodes as a java agent, Apache Cassandra sends metrics and other structured events 
   to collectd over a local unix socket.  
   
   * Fast and efficient.  It can track over 100k unique metric series per node (i.e. hundreds of tables). 
     
   * Comes with extensive dashboards out of the box, built on [prometheus](http://prometheus.io) and [grafana](http://grafana.com).  
     The Cassandra dashboards let you aggregate latency accurately across all nodes, dc or rack, down to an individual table.   
     
     ![](.screenshots/os.png)
     ![](.screenshots/cluster.png)
   
## Design Principles

  * Little/No performance impact to C* 
  * Simple to install and self managed
  * Collect all OS and C* metrics by default
  * Keep historical metrics on node for analysis
  * Provide useful integration with prometheus and grafana
      
## Installation of Agent
    
 1. Download the [latest release](https://github.com/datastax/metric-collector-for-apache-cassandra/releases/latest) of the agent onto your Cassandra nodes.
 The archive is self contained so no need do anything other than `tar -zxf latest.tar.gz` 
 into any location you prefer like `/usr/local` or `/opt`.

 2. Add the following line into the `cassandra-env.sh` file:
     
     ````
     MCAC_ROOT=/path/to/directory 
     JVM_OPTS="$JVM_OPTS -javaagent:${MCAC_ROOT}/lib/cassandra-mcac-agent.jar"
     ````
 3. Bounce the node.  
 
 On restart you should see `'Starting DataStax Metric Collector for Apache Cassandra'` in the Cassandra system.log 
 and the prometheus exporter will be available on port `9103`
 
 The [config/metric-collector.yaml](config/metrics-collector.yaml) file requires no changes by default but please read and add any customizations like
 filtering of metrics you don't need. 
 
 The [config/collectd.conf.tmpl](config/collectd.conf.tmpl) file can also be edited to change default collectd plugins enabled.  But it's recommended
 you use the [include path pattern](https://collectd.org/documentation/manpages/collectd.conf.5.shtml#include_path_pattern) 
 to configure extra plugins.

## Installing the Prometheus Dashboards

 1. Download the [latest release](https://github.com/datastax/metric-collector-for-apache-cassandra/releases/latest) of the dashboards and unzip.
 
 2. Install [Docker compose](https://docs.docker.com/compose/install/)
 
 3. Add the list of C* nodes with running agents to [tg_mcac.json](dashboards/prometheus/tg_mcac.json)
  
 4. `docker-compose up` will start everything and begin collection of metrics

 5. The Grafana web ui runs on port `3000` and the prometheus web ui runs on port `9000`
     
 If you have an existing prometheus setup you will need the dashboards and [relabel config](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#relabel_config) from the
 included [prometheus.yaml](dashboards/prometheus/prometheus.yaml) file.
 
 
## FAQ
  1. Where is the list of all Cassandra metrics?
  
     The full list is located on [Apache Cassandra docs](https://cassandra.apache.org/doc/latest/operating/metrics.html) site.
     The names are automatically changed from CamelCase to snake_case.
  
     In the case of prometheus the metrics are further renamed based on [relabel config](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#relabel_config) which live in the 
     [prometheus.yaml](dashboards/prometheus/prometheus.yaml) file.
  
  2. How can I filter out metrics I don't care about?
     
     Please read the [metric-collector.yaml](config/metrics-collector.yaml) section on how to add filtering rules.
  
  3. What is the datalog? and what is it for?
      
     The datalog is a space limited JSON based structured log of metrics and events which are optionally kept on each node.  
     It can be useful to diagnose issues that come up with your cluster.  If you wish to use the logs yourself
     there's a [script](scripts/datalog-parser.py) included to parse these logs which can be analyzed or piped 
     into [jq](https://stedolan.github.io/jq/).
     
     Alternatively, DataStax offers free support for issues as part of our [keep calm](https://www.datastax.com/keepcalm) 
     initiative and these logs can help our support engineers help diagnose your problem.
     
  4. Will the MCAC agent work on a Mac?
     
     No. It can be made to but it's currently only supported on Linux based OS.
          
## License

Copyright DataStax, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## Kubernetes Support
These dashboards and prometheus configuration files may be transformed via the collection of Python scripts under `bin/`.

Specifically run `bin/clean.py && bin/build.py` to generate appropriate files in the `dashboards/k8s-build/generated` directory. These files integrate with the Custom Resources defined by the [Prometheus](https://operatorhub.io/operator/prometheus) and [Grafana](https://operatorhub.io/operator/grafana-operator) operators available on Operator Hub.


### Prometheus
The [Prometheus Operator](https://operatorhub.io/operator/prometheus) handles the orchestration, configuration, and deployment of k8s resources required for a HA Prometheus installation. Rather than specifying a list of C\* nodes in a JSON file we direct Prometheus to monitor a Kubernetes [Service](https://kubernetes.io/docs/concepts/services-networking/service/) which exposes all C\* nodes via DNS. This mapping of hosts is handled by a `ServiceMonitor` Custom Resource defined by the operator. The following steps illustrate how to install the Prometheus operator, deploy a service monitor pointed at a C\* cluster (with metric relabeling), and  deploy a HA prometheus cluster connected to the service monitor.

1. Install the OperatorHub Lifecycle Manager (OLM)
   
   `curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/0.14.1/install.sh | bash -s 0.14.1`
   
   This installs a number of custom resource definitions and an operator that handles installing _other_ operators.

1. Install the Prometheus Operator
   
   `kubectl create -n cass-operator -f dashboards/k8s-build/generated/prometheus/operator.yaml`

   This will pull down and start the Prometheus operator. The operator is installed in a new namespace `prometheus-operator` along with a configuration indicating it should watch the `default` namespace for resources it manages. Should the installation require different namespaces change the values within this file and re-apply it to the cluster.

1. Configure and install the Service Monitor
   
   Before installation edit the service monitor's YAML to include the appropriate labels that match your cluster's service. For example if your cluster service has the label `cassandra.datastax.com/cluster: cluster-name` this mapping would be included in the service monitor yaml under `spec.selector.matchLabels`. As a convenience we have included the example here in the generated `service_monitor.yaml`.

   _Note: To check the labels on your service run the following command_

   `kubectl get svc -n cass-operator --show-labels=true`
   
   With the configuration file updated apply the resource to the cluster.
   
   `kubectl apply -n cass-operator -f dashboards/k8s-build/generated/prometheus/service_monitor.yaml`

1. Configure and install the Prometheus deployment
   
   Similar to the service and service monitor mapping, the instance must be provided with a set of labels to match service monitors to Prometheus deployments. The following section in the `Prometheus` custom resource maps the deployment to all service monitors with the label `cassandra.datastax.com/cluster: cluster-name`
  
   ```
     serviceMonitorSelector:
       matchLabels:
         cassandra.datastax.com/cluster: cluster-name
   ```
   
   After adjustments apply this file to the cluster

   `kubectl apply -n cass-operator -f dashboards/k8s-build/generated/prometheus/instance.yaml`


### Grafana
The [Grafana Operator](https://operatorhub.io/operator/grafana-operator) handles the orchestration, configuration, and deployment of k8s resources required for a Grafana installation. Instead of configuring datasources, dashboards, and deployments via the GUI everything is configured via YAML files in this repo. The following steps illustrate how to install the Grafana operator, data source pointed at the previously deployed prometheus, a collection of dashboards, and an instance of the grafana application.

1. Install the OperatorHub Lifecycle Manager (OLM) if you haven't already
   
   `curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/0.14.1/install.sh | bash -s 0.14.1`
   
   This installs a number of custom resource definitions and an operator that handles installing _other_ operators.

1. Install the Grafana Operator
   
   `kubectl create -n cass-operator -f dashboards/k8s-build/generated/grafana/operator.yaml`

   This will pull down and start the Grafana operator. The operator is installed in a new namespace `grafana-operator` along with a configuration indicating it should watch the `default` namespace for resources it manages. Should the installation require different namespaces change the values within this file and re-apply it to the cluster.

1. Configure and install the `GrafanaDataSource`
   
   Before installation edit the YAML to point at the instantiated prometheus cluster. If you are not sure what value to use check the output of `kubectl get svc` for the prometheus service name.
   
   With the configuration file updated apply the resource to the cluster.
   
   `kubectl apply -n cass-operator -f dashboards/k8s-build/generated/grafana/datasource.yaml`

1. Configure and install the `GrafanaDashboard`
   
   Before installation edit the YAML with appropriate labels. In this example a label of `app=grafana` is used. With the configuration file updated apply the resource to the cluster.
   
   `kubectl apply -n cass-operator -f dashboards/k8s-build/generated/grafana/`

1. Configure and install the Grafana deployment
   
   The deployment must be informed of labels to check for matching dashboards. The following section in the `Grafana` custom resource maps the deployment to all dashboards with the label `app=grafana`
  
   ```
     dashboardLabelSelector:
       - matchExpressions:
           - key: app
             operator: In
             values:
               - grafana
   ```
   
   After adjustments apply this file to the cluster

   `kubectl apply -n cass-operator -f dashboards/k8s-build/generated/grafana/instance.yaml`

1. Port forward to the grafana instance and check it out at http://127.0.0.1:3000/ (username: admin, password: secret)
   
   `kubectl port-forward -n cass-operator svc/grafana-service 3000`
