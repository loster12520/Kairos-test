#!/usr/bin/env bash
# Starts the blog backend, waits for it, runs the Playwright acceptance and always stops it.
set -euo pipefail
cd "$(dirname "$0")/.."

LOG="build/blog-e2e.log"
mkdir -p build
./gradlew --console=plain :blog-backend:run > "$LOG" 2>&1 &
GRADLE_PID=$!
cleanup() {
  kill "$GRADLE_PID" 2>/dev/null || true
  # The application runs in a child JVM; stop it too so the port is released.
  pkill -f 'dev.kairos.blog.MainKt' 2>/dev/null || true
}
trap cleanup EXIT

for attempt in $(seq 1 120); do
  if curl -sf -o /dev/null http://127.0.0.1:8080/; then
    break
  fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    echo "blog backend exited before becoming ready"; tail -40 "$LOG"; exit 1
  fi
  sleep 1
done

curl -sf -o /dev/null http://127.0.0.1:8080/ || { echo "blog backend did not start"; tail -40 "$LOG"; exit 1; }

node tools/e2e.mjs
