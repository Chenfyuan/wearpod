#!/usr/bin/env bash

set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  bash scripts/deploy-relay.sh [git-ref]

Environment variables:
  REPO_OWNER       GitHub owner. Default: Chenfyuan
  REPO_NAME        Repository name. Default: wearpod
  REPO_REF         Git ref to deploy when no positional arg is provided. Default: main
  APP_DIR          Remote relay working directory. Default: /opt/wearpod-relay/app
  IMAGE_NAME       Docker image name. Default: wearpod-relay:latest
  CONTAINER_NAME   Docker container name. Default: wearpod-relay
  PORT             Relay container port. Default: 8787
  HOST_BIND        Host bind address. Default: 127.0.0.1
  PUBLIC_BASE_URL  Public relay base URL. Default: https://wearpod.linsblog.cn

Examples:
  bash scripts/deploy-relay.sh
  bash scripts/deploy-relay.sh 327d431d9a1469c588dbefd8ebae058c70e9884e
  PUBLIC_BASE_URL=https://relay.example.com bash scripts/deploy-relay.sh main
EOF
  exit 0
fi

REPO_OWNER="${REPO_OWNER:-Chenfyuan}"
REPO_NAME="${REPO_NAME:-wearpod}"
REF="${1:-${REPO_REF:-main}}"
APP_DIR="${APP_DIR:-/opt/wearpod-relay/app}"
IMAGE_NAME="${IMAGE_NAME:-wearpod-relay:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-wearpod-relay}"
PORT="${PORT:-8787}"
HOST_BIND="${HOST_BIND:-127.0.0.1}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://wearpod.linsblog.cn}"

BASE_JSDELIVR="https://cdn.jsdelivr.net/gh/${REPO_OWNER}/${REPO_NAME}@${REF}/relay"
BASE_RAW="https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/${REF}/relay"

fetch_file() {
  local file="$1"
  local url1="${BASE_JSDELIVR}/${file}"
  local url2="${BASE_RAW}/${file}"

  echo "==> downloading ${file}"
  curl -fLSo "${file}" "${url1}" || curl -fLSo "${file}" "${url2}"
}

mkdir -p "${APP_DIR}"
cd "${APP_DIR}"

fetch_file "Dockerfile"
fetch_file "package.json"
fetch_file "package-lock.json"
fetch_file "server.mjs"

echo "==> building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

echo "==> replacing container ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_BIND}:${PORT}:${PORT}" \
  -e "PORT=${PORT}" \
  -e "PUBLIC_BASE_URL=${PUBLIC_BASE_URL}" \
  "${IMAGE_NAME}"

echo "==> waiting for startup"
sleep 4

echo "==> import health check"
curl -fsS "http://${HOST_BIND}:${PORT}/api/sessions" -X POST
echo

echo "==> export health check"
curl -fsS "http://${HOST_BIND}:${PORT}/api/export-sessions" \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"opmlContent":"<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"1.0\"><head><title>WearPod Export</title></head><body><outline text=\"Demo\" title=\"Demo\" type=\"rss\" xmlUrl=\"https://example.com/feed.xml\"/></body></opml>","outlineCount":1}'
echo

echo "==> done"
echo "deployed ref: ${REF}"
echo "public base: ${PUBLIC_BASE_URL}"
