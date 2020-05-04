#!/bin/bash

for file in dashboards-jsonnet/*; do
  name=$(basename $file);
  docker run -v `pwd`:/here datastax/grafonnet-lib:v0.1.0 jsonnet --ext-str prefix=mcac /here/$file > generated-dashboards/${name%.jsonnet}.json;
done
