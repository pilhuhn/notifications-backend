 set -x
 curl -i -X POST \
 http://localhost:8085/ \
-H "Ce-Id: 1234" \
-X POST \
-H "Ce-Id: 1234" \
-H "Ce-Specversion: 1.0" \
-H "Ce-Type: com.redhat.cloud.notification" \
-H "Ce-Source: curl" \
-H "Content-Type: application/json" \
-d @- << -EOF-
{
  "bundle": "a-bundle",
  "application": "my-app",
  "event_type": "et2",
  "timestamp": "2020-12-08T09:31:39Z",
  "account_id": "54321",
  "events": [
    {
      "metadata": {},
      "payload": "{
        \"any\" : \"thing\",
        \"you\": 1,
        \"want\" : \"here\",
        \"key1\" : \"Heiko\",
        \"key2\" : \"Thomas\"
      }"
    }
  ],
  "context": "{
    \"any\" : \"thing\",
    \"you\": 1,
    \"want\" : \"here\"
  }"
}
-EOF-
