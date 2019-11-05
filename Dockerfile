FROM cassandra:2.2
#FROM cassandra:3.0
#FROM cassandra:3.11

COPY config/metrics-collector.yaml /etc/cassandra/metrics-collector.yaml
COPY target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar /etc/cassandra/datastax-mcac-agent-0.1.0-SNAPSHOT.jar
COPY target/collectd /etc/cassandra/collectd
COPY config/collectd.conf.tmpl /etc/cassandra/
COPY config/scribe.conf.tmpl /etc/cassandra/

RUN echo "JVM_OPTS=\"\$JVM_OPTS -Dds-metric-collector.config=file:///etc/cassandra/metrics-collector.yaml\"" >> /etc/cassandra/cassandra-env.sh 
RUN echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mcac-agent-0.1.0-SNAPSHOT.jar\"" >> /etc/cassandra/cassandra-env.sh 

#debugging
#RUN echo "JVM_OPTS=\"\$JVM_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005\"" >> /etc/cassandra/cassandra-env.sh 
#EXPOSE 5005


