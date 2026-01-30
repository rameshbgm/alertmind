# Fix for ElevenLabs 404 Error

## Problem
The application was getting a 404 error when trying to create outbound calls through ElevenLabs API:
```
ElevenLabs call API error - Status: 404 NOT_FOUND, Body: {"detail":"Not Found"}
```

## Root Cause
Two issues were identified:

1. **Incorrect API Endpoint Path**
   - Was using: `/v1/convai/phone-calls`
   - Should be: `/v1/convai/conversation/phone_call`

2. **Missing Configuration Values**
   - `ELEVENLABS_AGENT_ID` was set to placeholder
   - `ELEVENLABS_AGENT_PHONE_NUMBER_ID` was set to placeholder

## Fix Applied

### 1. Updated API Endpoint
Fixed `application.yaml`:
```yaml
calls-path: /v1/convai/conversation/phone_call
```

### 2. Updated Local Config
Updated `application-local.yaml` with your provided values:
```yaml
ELEVENLABS_AGENT_ID: agent_01k08m7k11e0yspnsnxtye7dct
ELEVENLABS_AGENT_PHONE_NUMBER_ID: phnum_1701k8nv8jx8fs9amj03v3w578ev
```

## Testing the Fix

1. **Restart the application** (the config changes require a restart):
   ```bash
   # Stop the current running application (Ctrl+C)
   ./gradlew bootRun
   ```

2. **Test the API** with the same request:
   ```bash
   curl -X POST http://localhost:8080/api/agent-calls \
     -H "Content-Type: application/json" \
     -d '{
       "toNumber": "+6586024972",
       "incidentNumber": "123",
       "priority": "1",
       "shortDescription": "Test",
       "description": "there is an incident with number 123",
       "incidentDateTime": "2026-01-30",
       "errorDetails": "SEVERE: Uncaught Exception in thread main: java.lang.OutOfMemoryError",
       "possibleFix": "Immediate: Restart the payment-service-prod container"
     }'
   ```

3. **Expected Success Response**:
   ```json
   {
     "callId": "call_xxx",
     "rawResponse": { ... }
   }
   ```

## Verify in Logs
You should see:
```
INFO  : Received createCall request to +6586024972 for incident 123
INFO  : Creating ElevenLabs outbound call to: +6586024972
INFO  : Successfully created call: call_xxx
```

## MongoDB Verification
Check the `agent_calls` collection in MongoDB:
```javascript
use alertmind
db.agent_calls.find().pretty()
```

You should see:
- `requestPayload` - The original request
- `rawResponse` - The ElevenLabs API response
- `callId` - The call identifier
- `status` - Initial call status

## Next Steps
Once the call is successful, the webhook at `/api/webhooks/elevenlabs/call-status` will receive status updates and automatically:
1. Update the call status in MongoDB
2. Fetch and save the call transcript when the call completes
3. Log failure reasons if the call fails

## Troubleshooting
If you still get errors:
- Verify the agent ID and phone number ID are correct in your ElevenLabs dashboard
- Check that your ElevenLabs API key has permissions for outbound calls
- Ensure the agent is configured with Twilio integration in ElevenLabs
