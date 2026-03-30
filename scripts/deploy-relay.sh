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
  ASSET_DIR        Remote docs assets directory. Default: /opt/wearpod-relay/docs/assets
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
ASSET_DIR="${ASSET_DIR:-/opt/wearpod-relay/docs/assets}"
IMAGE_NAME="${IMAGE_NAME:-wearpod-relay:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-wearpod-relay}"
PORT="${PORT:-8787}"
HOST_BIND="${HOST_BIND:-127.0.0.1}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://wearpod.linsblog.cn}"

BASE_JSDELIVR="https://cdn.jsdelivr.net/gh/${REPO_OWNER}/${REPO_NAME}@${REF}"
BASE_RAW="https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/${REF}"

fetch_file() {
  local repo_path="$1"
  local target_path="$2"
  local url1="${BASE_RAW}/${repo_path}"
  local url2="${BASE_JSDELIVR}/${repo_path}"

  echo "==> downloading ${repo_path}"
  curl -fLSo "${target_path}" "${url1}" || curl -fLSo "${target_path}" "${url2}"
}

mkdir -p "${APP_DIR}" "${ASSET_DIR}"
cd "${APP_DIR}"

fetch_file "relay/Dockerfile" "${APP_DIR}/Dockerfile"
fetch_file "relay/package.json" "${APP_DIR}/package.json"
fetch_file "relay/package-lock.json" "${APP_DIR}/package-lock.json"
fetch_file "relay/server.mjs" "${APP_DIR}/server.mjs"

assets=(
  "wearpod-cover.svg"
  "wearpod-detail.png"
  "wearpod-downloads.png"
  "wearpod-home.png"
  "wearpod-player.png"
  "wearpod-queue.png"
  "wearpod-subscriptions.png"
)

for asset in "${assets[@]}"; do
  fetch_file "docs/assets/${asset}" "${ASSET_DIR}/${asset}"
done

echo "==> building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

echo "==> replacing container ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_BIND}:${PORT}:${PORT}" \
  -v "${ASSET_DIR}:/docs/assets:ro" \
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
echo "asset dir: ${ASSET_DIR}"
