#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd $DIR
for file in dashboards-jsonnet/*; do
  name=$(basename $file);
  echo "Generating ${name%.jsonnet}.json"
  docker run -v `pwd`:/here datastax/grafonnet-lib:v0.1.1 jsonnet --ext-str prefix=mcac /here/$file > `pwd`/generated-dashboards/${name%.jsonnet}.json;
done
