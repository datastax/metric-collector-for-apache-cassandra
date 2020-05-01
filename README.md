Metric Collector for Apache Cassandra&reg; (MCAC)
=================================================

Allows metric collection on Apache Cassandra (2.2, 3.0, 3.11, 4.0)
clusters.

![CI](https://github.com/datastax/metric-collector-for-apache-cassandra/workflows/Java%20CI/badge.svg)
![Release](https://github.com/datastax/metric-collector-for-apache-cassandra/workflows/Docker%20Release/badge.svg)
## Introduction

   Metric Collector for Apache Cassandra (MCAC) aggregates OS and C* metrics and integrates with existing monitoring 
   solutions to facilitate problem resolution and remediation.
   
   MCAC comes bundled with and is built on [collectd](https://collectd.org), a popular, well-supported, open source metric collection agent. 
   With over 90 plugins, you can tailor the solution to collect metrics most important to your organization.
   
   MCAC is easily added to Cassandra nodes as a java agent, Apache Cassandra sends metrics and other structured events to collectd Use dsetool insights_config to enable and configure the frequency and type of metrics that are sent to DSE Metrics Collector. After setting the configuration properties, you can export the aggregated metrics to monitoring tools like Prometheus, Graphite, and Splunk, which can then be visualized in a dashboard such as Grafana.
   
   MCAC can track > 100k series 
     
## Design Principles
  * Little/No performance impact to C* 
  * Simple to install and self managed
  * Collect all OS and C* metrics by default
  * Keep historical metrics on node for analysis
  * Provide useful integration with prometheus and grafana
  
  The Management API has no configuration file.  Rather, it can only be configured from a 
  small list of command line flags.  Communication by default can only be via **unix socket** 
  or via a **http(s) endpoint** with optional TLS client auth.
  
  In a containerized setting the Management API represents **PID 1** and will be 
  responsible for the lifecycle of Cassandra&reg; via the API.
  
  Communication between the Management API and Cassandra&reg; is via a local **unix socket** using
  CQL as it's only protocol.  This means, out of the box Cassandra&reg; can be started
  securely with no open ports!  Also, using CQL only means operators can
  execute operations via CQL directly if they wish.
  
  Each Management API is responsible for the local node only.  Coordination across nodes
  is up to the caller.  That being said, complex health checks can be added via CQL.
    
## Usage
    
 Download the [latest release]() and install it on each node.
 The [configuration file]() includes     
    
## Building
     1) If you want the agent to start every time when you start Cassandra, append the following line to cassandra-env.sh located in your Cassandra conf directory:

       JVM_OPTS="$JVM_OPTS -javaagent:/path/to/this/project/./lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar"
  
     You need to restart Cassandra after the configuration change!

     Example:
       JVM_OPTS="$JVM_OPTS -javaagent:/home/ubuntu/metric-collector-for-apache-cassandra/lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar"
    
     For one-time start of the agent, start Cassandra with the following command:
     
     JVM_EXTRA_OPTS="-javaagent:/path/to/this/project/./lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar" ./bin/cassandra -f

   
  
## Roadmap
  * CQL based configuration changes
  * Configuration as system table

## License

Copyright DataStax, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

Currently to use follow the instructions below:

