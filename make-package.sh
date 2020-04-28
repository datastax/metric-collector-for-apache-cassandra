#!/bin/bash

VERSION=$(cat src/main/resources/build_version)
PROJECT_DIR_NAME=mcac-$VERSION

mvn -DskipTests package
mkdir -p $PROJECT_DIR_NAME/config
cp config/collectd.conf.tmpl $PROJECT_DIR_NAME/config
cp config/metrics-collector.yaml $PROJECT_DIR_NAME/config
cp config/prom.conf $PROJECT_DIR_NAME/config
cp config/scribe.conf.tmpl $PROJECT_DIR_NAME/config

cp README.md $PROJECT_DIR_NAME
cp LICENSE.md $PROJECT_DIR_NAME
mkdir $PROJECT_DIR_NAME/target
cp -r target/collectd $PROJECT_DIR_NAME/target
cp target/datastax-mcac-agent-$VERSION-SNAPSHOT.jar $PROJECT_DIR_NAME/target
mv $PROJECT_DIR_NAME/target $PROJECT_DIR_NAME/lib
mv $PROJECT_DIR_NAME/lib/datastax-mcac-agent-$VERSION-SNAPSHOT.jar $PROJECT_DIR_NAME/lib/cassandra-mcac-agent-$VERSION.jar
tar zcvf $PROJECT_DIR_NAME.tar.gz $PROJECT_DIR_NAME
zip $PROJECT_DIR_NAME.zip $(tar ztf $PROJECT_DIR_NAME.tar.gz)