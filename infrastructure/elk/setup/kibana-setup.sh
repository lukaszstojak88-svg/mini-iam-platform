#!/bin/sh
# Creates the `mini-iam-logs-*` data view in Kibana (spec §16.4). Idempotent (fixed id + override).
set -eu

: "${ELASTIC_PASSWORD:?ELASTIC_PASSWORD is required}"

KIBANA_URL="http://kibana:5601"

echo "Waiting for Kibana..."
until curl -s -f -o /dev/null -u "elastic:${ELASTIC_PASSWORD}" "${KIBANA_URL}/api/status"; do
  sleep 5
done

echo "Creating data view mini-iam-logs-*"
curl -sS -f -o /dev/null -u "elastic:${ELASTIC_PASSWORD}" \
  -X POST "${KIBANA_URL}/api/data_views/data_view" \
  -H "kbn-xsrf: true" -H "Content-Type: application/json" \
  -d '{
    "override": true,
    "data_view": {
      "id": "mini-iam-logs",
      "name": "Mini IAM logs",
      "title": "mini-iam-logs-*",
      "timeFieldName": "@timestamp"
    }
  }'

echo "Kibana setup finished"
