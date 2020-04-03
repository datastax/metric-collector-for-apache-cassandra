#!/bin/bash

mvn -DskipTests package
mkdir OSS-Collector
mkdir OSS-Collector/config
cp config/collectd.conf.tmpl OSS-Collector/config
cp config/metrics-collector.yaml OSS-Collector/config
cp config/prom.conf OSS-Collector/config
cp config/scribe.conf.tmpl  OSS-Collector/config

cp README.md OSS-Collector
cp LICENSE.md OSS-Collector
mkdir OSS-Collector/target
cp -r target/collectd OSS-Collector/target
cp target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar OSS-Collector/target
mv OSS-Collector/target OSS-Collector/lib
mv OSS-Collector/lib/datastax-mcac-agent-0.1.0-SNAPSHOT.jar OSS-Collector/lib/cassandra-mcac-agent-0.1.0-SNAPSHOT.jar
tar zcvf OSS-Collector.tar.gz OSS-Collector
