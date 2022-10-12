FROM cassandra:4.0

RUN mkdir -p /opt/mcac/config
RUN mkdir -p /opt/mcac/lib
COPY src/test/resources/metric-collector.yaml /opt/mcac/config/metric-collector.yaml
COPY target/datastax-mcac-agent-*.jar /opt/mcac/lib/datastax-mcac-agent.jar
COPY target/collectd /opt/mcac/lib/collectd
COPY config/collectd.conf.tmpl /opt/mcac/config

RUN echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/opt/mcac/lib/datastax-mcac-agent.jar -Dcassandra.ring_delay_ms=0\"" >> /etc/cassandra/cassandra-env.sh

EXPOSE 9103