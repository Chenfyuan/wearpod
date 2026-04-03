#!/usr/bin/env bash

set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  bash scripts/deploy-relay.sh [git-ref]

Environment variables:
  REPO_OWNER       GitHub owner. Default: Chenfyuan
  REPO_NAME        Repository name. Default: wearpod
  REPO_REF         Git ref to deploy when no positional arg is provided. Default: latest
  APP_DIR          Remote relay working directory. Default: /opt/wearpod-relay/app
  ASSET_DIR        Remote docs assets directory. Default: /opt/wearpod-relay/docs/assets
  IMAGE_NAME       Docker image name. Default: wearpod-relay:latest
  CONTAINER_NAME   Docker container name. Default: wearpod-relay
  PORT             Relay container port. Default: 8787
  HOST_BIND        Host bind address. Default: 127.0.0.1
  FALLBACK_PORT    Optional public fallback port mapped to the relay port. Default: 8080
  PUBLIC_BASE_URL  Public relay base URL. Default: https://wearpod.linsblog.cn

Examples:
  bash scripts/deploy-relay.sh
  bash scripts/deploy-relay.sh latest
  bash scripts/deploy-relay.sh 327d431d9a1469c588dbefd8ebae058c70e9884e
  PUBLIC_BASE_URL=https://relay.example.com bash scripts/deploy-relay.sh main
EOF
  exit 0
fi

REPO_OWNER="${REPO_OWNER:-Chenfyuan}"
REPO_NAME="${REPO_NAME:-wearpod}"
REQUESTED_REF="${1:-${REPO_REF:-latest}}"
APP_DIR="${APP_DIR:-/opt/wearpod-relay/app}"
ASSET_DIR="${ASSET_DIR:-/opt/wearpod-relay/docs/assets}"
ASSET_EN_DIR="${ASSET_EN_DIR:-${ASSET_DIR}/en}"
IMAGE_NAME="${IMAGE_NAME:-wearpod-relay:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-wearpod-relay}"
PORT="${PORT:-8787}"
HOST_BIND="${HOST_BIND:-127.0.0.1}"
FALLBACK_PORT="${FALLBACK_PORT:-8080}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://wearpod.linsblog.cn}"

resolve_ref() {
  local ref="$1"
  local branch_ref="$ref"

  if [[ "${ref}" == "latest" ]]; then
    branch_ref="main"
  fi

  if [[ "${branch_ref}" =~ ^[0-9a-fA-F]{7,40}$ ]]; then
    echo "${branch_ref}"
    return
  fi

  local api_url="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/commits/${branch_ref}"
  local resolved_sha
  resolved_sha="$(
    curl -fsSL "${api_url}" | python3 -c 'import json, sys; print(json.load(sys.stdin)["sha"])'
  )"

  if [[ -z "${resolved_sha}" ]]; then
    echo "Failed to resolve git ref: ${ref}" >&2
    exit 1
  fi

  echo "${resolved_sha}"
}

RESOLVED_REF="$(resolve_ref "${REQUESTED_REF}")"
BASE_JSDELIVR="https://cdn.jsdelivr.net/gh/${REPO_OWNER}/${REPO_NAME}@${RESOLVED_REF}"
BASE_RAW="https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/${RESOLVED_REF}"

fetch_file() {
  local repo_path="$1"
  local target_path="$2"
  local url1="${BASE_RAW}/${repo_path}"
  local url2="${BASE_JSDELIVR}/${repo_path}"

  echo "==> downloading ${repo_path}"
  curl -fLSo "${target_path}" "${url1}" || curl -fLSo "${target_path}" "${url2}"
}

mkdir -p "${APP_DIR}" "${ASSET_DIR}" "${ASSET_EN_DIR}"
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

for asset in "${assets[@]}"; do
  fetch_file "docs/assets/en/${asset}" "${ASSET_EN_DIR}/${asset}"
done

echo "==> building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

echo "==> replacing container ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_BIND}:${PORT}:${PORT}" \
  -p "${FALLBACK_PORT}:${PORT}" \
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
echo "requested ref: ${REQUESTED_REF}"
echo "resolved ref: ${RESOLVED_REF}"
echo "public base: ${PUBLIC_BASE_URL}"
echo "asset dir: ${ASSET_DIR}"
echo "asset en dir: ${ASSET_EN_DIR}"
echo "fallback port: ${FALLBACK_PORT}"
