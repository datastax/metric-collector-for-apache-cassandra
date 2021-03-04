#!/bin/bash

# Package version. Mandatory.
VERSION=$1
# Cassandra version to use for compilation, e.g. 3.11.10. Optional.
CASSANDRA_VERSION=$2
# Compilation profile for this package, look into pom.xml for profile list. Optional.
BUILD_PROFILE=$3

if [ "$VERSION" == "" ]; then
  echo "Missing version arg"
  exit 1
fi

OPTIONAL_MVN_ARGS=""
if [ -n "$CASSANDRA_VERSION" ]; then
  OPTIONAL_MVN_ARGS="${OPTIONAL_MVN_ARGS} -Dcassandra.version=${CASSANDRA_VERSION}"
fi
if [ -n "$BUILD_PROFILE" ]; then
  OPTIONAL_MVN_ARGS="${OPTIONAL_MVN_ARGS} --activate-profiles=${BUILD_PROFILE}"
fi

PACKAGE_DIR=package-$VERSION
PROJECT_DIR_NAME=datastax-mcac-agent-$VERSION

mkdir $PACKAGE_DIR

mvn -DskipTests clean package -Drevision=$VERSION $OPTIONAL_MVN_ARGS
mkdir -p $PACKAGE_DIR/$PROJECT_DIR_NAME/config
cp config/collectd.conf.tmpl $PACKAGE_DIR/$PROJECT_DIR_NAME/config
cp config/metric-collector.yaml $PACKAGE_DIR/$PROJECT_DIR_NAME/config

cp README.md $PACKAGE_DIR/$PROJECT_DIR_NAME
cp LICENSE.txt $PACKAGE_DIR/$PROJECT_DIR_NAME
mkdir $PACKAGE_DIR/$PROJECT_DIR_NAME/lib
cp -r target/collectd $PACKAGE_DIR/$PROJECT_DIR_NAME/lib
cp target/datastax-mcac-agent-$VERSION.jar $PACKAGE_DIR/$PROJECT_DIR_NAME/lib/datastax-mcac-agent.jar
cp -r scripts $PACKAGE_DIR/$PROJECT_DIR_NAME/
pushd .
cd $PACKAGE_DIR
tar zcvf $PROJECT_DIR_NAME.tar.gz $PROJECT_DIR_NAME
zip $PROJECT_DIR_NAME.zip $(tar ztf $PROJECT_DIR_NAME.tar.gz)
popd
pushd .
cd dashboards/grafana
./make-dashboards.sh
popd

DASHBOARD_DIR_NAME=datastax-mcac-dashboards-$VERSION
mkdir -p $PACKAGE_DIR/$DASHBOARD_DIR_NAME/grafana
cp dashboards/docker-compose.yaml $PACKAGE_DIR/$DASHBOARD_DIR_NAME
cp -r dashboards/prometheus $PACKAGE_DIR/$DASHBOARD_DIR_NAME
cp dashboards/grafana/prometheus-datasource.yaml $PACKAGE_DIR/$DASHBOARD_DIR_NAME/grafana
cp dashboards/grafana/dashboards.yaml $PACKAGE_DIR/$DASHBOARD_DIR_NAME/grafana
cp -r dashboards/grafana/generated-dashboards $PACKAGE_DIR/$DASHBOARD_DIR_NAME/grafana
pushd .
cd $PACKAGE_DIR
tar zcvf $DASHBOARD_DIR_NAME.tar.gz $DASHBOARD_DIR_NAME
zip $DASHBOARD_DIR_NAME.zip $(tar ztf $DASHBOARD_DIR_NAME.tar.gz)
popd

