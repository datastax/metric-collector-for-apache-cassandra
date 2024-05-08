#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker run --rm -v "${ROOT_DIR}:${ROOT_DIR}" datastax/grafonnet-lib:v0.1.3 \
  jsonnet "${ROOT_DIR}/mixin/alerts.jsonnet"
