#!/usr/bin/env bash
set -euo pipefail

# Execute the README's host-Maven route with the optional localhost-only DB port.
mkdir -p test-results
maven_pid=
cleanup() {
  if [ -n "$maven_pid" ]; then
    kill -- "-$maven_pid" 2>/dev/null || true
    wait "$maven_pid" 2>/dev/null || true
  fi
  cat test-results/maven-startup.log
  docker compose -f compose.yaml -f compose.maven.yaml down
}
trap cleanup EXIT
docker compose -f compose.yaml -f compose.maven.yaml up -d --wait db
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${DB_PORT:-5432}/${POSTGRES_DB:-expenseflow}"
export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-expenseflow}"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export SPRING_PROFILES_ACTIVE=demo
export SERVER_PORT=8081
setsid ./mvnw --batch-mode spring-boot:run >test-results/maven-startup.log 2>&1 &
maven_pid=$!
for attempt in $(seq 1 90); do
  if curl --fail --silent http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
    curl --fail --silent http://localhost:8081/login | grep -q 'ユーザー名'
    echo "README Maven startup: health UP, login HTTP 200"
    exit 0
  fi
  kill -0 "$maven_pid"
  sleep 2
done
echo "Timed out waiting for README Maven startup" >&2
exit 1
