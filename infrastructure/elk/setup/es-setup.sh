#!/bin/sh
# One-off Elasticsearch setup (spec §16.4):
#  - password of the built-in kibana_system user,
#  - Logstash role and user allowed to write only to mini-iam-logs-* indices.
# Idempotent - safe to run multiple times.
set -eu

: "${ELASTIC_PASSWORD:?ELASTIC_PASSWORD is required}"
: "${KIBANA_SYSTEM_PASSWORD:?KIBANA_SYSTEM_PASSWORD is required}"
: "${LOGSTASH_WRITER_PASSWORD:?LOGSTASH_WRITER_PASSWORD is required}"

ES_URL="http://elasticsearch:9200"

es() {
  curl -sS -f -o /dev/null -u "elastic:${ELASTIC_PASSWORD}" -H "Content-Type: application/json" "$@"
}

echo "Waiting for Elasticsearch..."
until curl -s -o /dev/null -u "elastic:${ELASTIC_PASSWORD}" "${ES_URL}/_cluster/health?wait_for_status=yellow&timeout=5s"; do
  sleep 3
done

echo "Setting kibana_system password"
es -X POST "${ES_URL}/_security/user/kibana_system/_password" \
  -d "{\"password\":\"${KIBANA_SYSTEM_PASSWORD}\"}"

echo "Creating role mini_iam_logs_writer"
es -X PUT "${ES_URL}/_security/role/mini_iam_logs_writer" -d '{
  "cluster": ["monitor"],
  "indices": [
    { "names": ["mini-iam-logs-*"], "privileges": ["create_index", "create_doc", "write", "auto_configure"] }
  ]
}'

echo "Creating user logstash_writer"
es -X PUT "${ES_URL}/_security/user/logstash_writer" \
  -d "{\"password\":\"${LOGSTASH_WRITER_PASSWORD}\",\"roles\":[\"mini_iam_logs_writer\"]}"

echo "Elasticsearch setup finished"
