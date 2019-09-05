FROM datastax/ddac

COPY config/metrics-collector.yaml /opt/cassandra/metrics-collector.yaml
COPY target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar /opt/cassandra/datastax-mcac-agent-0.1.0-SNAPSHOT.jar
COPY target/collectd /opt/cassandra/collectd
COPY config/collectd.conf.tmpl /opt/cassandra/
COPY config/scribe.conf.tmpl /opt/cassandra/

RUN echo "JVM_OPTS=\"\$JVM_OPTS -Dds-metric-collector.config=file:///opt/cassandra/metrics-collector.yaml\"" >> /opt/cassandra/conf/cassandra-env.sh 
RUN echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/opt/cassandra/datastax-mcac-agent-0.1.0-SNAPSHOT.jar\"" >> /opt/cassandra/conf/cassandra-env.sh 

#debugging
#RUN echo "JVM_OPTS=\"\$JVM_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005\"" >> /opt/cassandra/conf/cassandra-env.sh 
#EXPOSE 5005

ENV DS_LICENSE accept

