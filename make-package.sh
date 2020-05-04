#!/bin/bash

VERSION=$1

if [ "$VERSION" == "" ]; then
  echo "Missing version arg"
  exit 1
fi

PROJECT_DIR_NAME=datastax-mcac-agent-$VERSION

mvn -DskipTests clean package -Drevision=$VERSION
mkdir -p $PROJECT_DIR_NAME/config
cp config/collectd.conf.tmpl $PROJECT_DIR_NAME/config
cp config/metrics-collector.yaml $PROJECT_DIR_NAME/config

cp README.md $PROJECT_DIR_NAME
cp LICENSE.md $PROJECT_DIR_NAME
mkdir $PROJECT_DIR_NAME/target
cp -r target/collectd $PROJECT_DIR_NAME/target
cp target/datastax-mcac-agent-$VERSION-SNAPSHOT.jar $PROJECT_DIR_NAME/target
mv $PROJECT_DIR_NAME/target $PROJECT_DIR_NAME/lib
mv $PROJECT_DIR_NAME/lib/datastax-mcac-agent-$VERSION-SNAPSHOT.jar $PROJECT_DIR_NAME/lib/datastax-mcac-agent.jar
tar zcvf $PROJECT_DIR_NAME.tar.gz $PROJECT_DIR_NAME
zip $PROJECT_DIR_NAME.zip $(tar ztf $PROJECT_DIR_NAME.tar.gz)

cd dashboards
./make-dashboards.sh


DASHBOARD_DIR_NAME=datastax-mcac-dashboards-$VERSION
mkdir -p $DASHBOARD_DIR_NAME/grafana
cp dashboards/docker-compose.yml $DASHBOARD_DIR_NAME
cp -r dashboards/prometheus $DASHBOARD_DIR_NAME
cp dashboards/grafana/prometheus-datasource.yaml $DASHBOARD_DIR_NAME/grafana
cp dashboards/grafana/dashboards.yaml $DASHBOARD_DIR_NAME/grafana
cp -r dashboards/grafana/generated-dashboards $DASHBOARD_DIR_NAME/grafana
tar zcvf $DASHBOARD_DIR_NAME.tar.gz $DASHBOARD_DIR_NAME
zip $DASHBOARD_DIR_NAME.zip $(tar ztf $DASHBOARD_DIR_NAME.tar.gz)
