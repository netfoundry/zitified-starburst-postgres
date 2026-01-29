#!/bin/bash
# 1. Start the query and grab the first nextUri
RESPONSE=$(curl -s -X POST http://localhost:8080/v1/statement \
  -H "X-Trino-User: admin" \
  -d "SELECT * FROM ziti_pg.public.transform LIMIT 5")

NEXT_URI=$(echo $RESPONSE | jq -r '.nextUri')
echo "Initial NextURI: $NEXT_URI"

# 2. Immediately poll the nextUri in a loop
while [ "$NEXT_URI" != "null" ]; do
  echo "Polling: $NEXT_URI"
  RESPONSE=$(curl -s "$NEXT_URI")
  NEXT_URI=$(echo $RESPONSE | jq -r '.nextUri')
  STATE=$(echo $RESPONSE | jq -r '.stats.state')
  echo "Current State: $STATE"
  
  # If data appears, break and show it
  DATA=$(echo $RESPONSE | jq -r '.data')
  if [ "$DATA" != "null" ]; then
    echo "DATA FOUND:"
    echo $RESPONSE | jq '.data'
    break
  fi
  sleep 1
done
