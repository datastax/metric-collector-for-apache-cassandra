FROM datastax/cassandra:4.0

COPY src/test/resources/metrics-collector.yaml /etc/cassandra/metrics-collector.yaml
COPY target/datastax-mcac-agent-*.jar /etc/cassandra/datastax-mcac-agent.jar
COPY target/collectd /etc/cassandra/collectd
COPY config/collectd.conf.tmpl /etc/cassandra/
COPY config/scribe.conf.tmpl /etc/cassandra/
COPY config/prom.conf /etc/cassandra/collectd/

RUN grep -qxF "JVM_OPTS=\"\$JVM_OPTS -Dds-metric-collector.config=file:///etc/cassandra/metrics-collector.yaml\"" /etc/cassandra/cassandra-env.sh || echo "JVM_OPTS=\"\$JVM_OPTS -Dds-metric-collector.config=file:///etc/cassandra/metrics-collector.yaml\"" >> /etc/cassandra/cassandra-env.sh
RUN grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mcac-agent.jar\"" || echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mcac-agent.jar\"" >> /etc/cassandra/cassandra-env.sh

EXPOSE 9103

#debugging
#RUN echo "JVM_OPTS=\"\$JVM_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005\"" >> /etc/cassandra/cassandra-env.sh
#EXPOSE 5005

