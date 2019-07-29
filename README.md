
Metric Collector for Apache Cassandra (MCAC)
============================================

Allows metric collector from DSE to run on OSS (3.0, 3.11, DDAC)
clusters.

Currently to use run the following commands:
     ````
     mvn -DskipTests package
     cd ~/cassandra_root
     JVM_EXTRA_OPTS="-javaagent:/path/to/this/project/./target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar" ./bin/cassandra -f
     ````


