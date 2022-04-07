#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/dashboards/grafana/generated-dashboards"

rm -rf "${OUTPUT_DIR}"
mkdir "${OUTPUT_DIR}"

docker run -v "${ROOT_DIR}:${ROOT_DIR}" datastax/grafonnet-lib:v0.1.3 \
  jsonnet --multi "${OUTPUT_DIR}" "${ROOT_DIR}/mixin/dashboards.jsonnet"
