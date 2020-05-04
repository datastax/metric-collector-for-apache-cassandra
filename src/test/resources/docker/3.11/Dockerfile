FROM cassandra:3.11


RUN mkdir -p /opt/mcac/config
RUN mkdir -p /opt/mcac/lib
COPY src/test/resources/metrics-collector.yaml /opt/mcac/config/metrics-collector.yaml
COPY target/datastax-mcac-agent-*.jar /opt/mcac/lib/datastax-mcac-agent.jar
COPY target/collectd /opt/mcac/lib/collectd
COPY config/collectd.conf.tmpl /opt/mcac/config

RUN grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/opt/mcac/lib/datastax-mcac-agent.jar\"" || echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/opt/mcac/lib/datastax-mcac-agent.jar\"" >> /etc/cassandra/cassandra-env.sh

EXPOSE 9103