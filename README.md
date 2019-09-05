
Metric Collector for Apache Cassandra (MCAC)
============================================

Allows metric collector from DSE to run on OSS (3.0, 3.11, DDAC)
clusters.

Currently to use run the following commands:
     
     mvn -DskipTests package
     
     cd ~/cassandra_root
     
     JVM_EXTRA_OPTS="-javaagent:/path/to/this/project/./target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar" ./bin/cassandra -f
     

To test with docker run the following commands:

    docker build -t ddac  ./ 

    docker run -p 5005:5005 --name ddac -t ddac 

To run with IDE debugging, uncomment the debugging line in the Dockerfile and

    docker run -p 5005:5005 --name ddac -t ddac 
