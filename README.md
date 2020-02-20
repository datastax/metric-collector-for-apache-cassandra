Metric Collector for Apache Cassandra (MCAC)
============================================

Allows metric collector to run on Apache Cassandra (2.2, 3.0, 3.11, DDAC)
clusters.

Currently to use follow the instructions below:

     1) If you want the agent to start every time when you start Cassandra, append the following line to cassandra-env.sh located in your Cassandra conf directory:

       JVM_OPTS="$JVM_OPTS -javaagent:/path/to/this/project/./lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar"
  
     You need to restart Cassandra after the configuration change!

     Example:
       JVM_OPTS="$JVM_OPTS -javaagent:/home/ubuntu/metric-collector-for-apache-cassandra/lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar"
    
     For one-time start of the agent, start Cassandra with the following command:
     
     JVM_EXTRA_OPTS="-javaagent:/path/to/this/project/./lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar" ./bin/cassandra -f

