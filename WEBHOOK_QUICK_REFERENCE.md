# ElevenLabs Webhook Implementation - Quick Reference

## ✅ Implementation Complete

Based on the official ElevenLabs documentation:
https://elevenlabs.io/docs/agents-platform/workflows/post-call-webhooks

## What Was Implemented

### 1. Comprehensive Webhook Handler
- **Endpoint**: `POST /api/webhooks/elevenlabs/call-status`
- **Handles all ElevenLabs webhook events**:
  - Success events: initiated, ringing, answered, completed, ended
  - Failure events: busy, no_answer, unreachable, rejected, initiation_failed, canceled, failed

### 2. Status Mapping
Each webhook event is mapped to a standardized status:
```
call.initiated → "initiated"
call.ringing → "ringing"
call.answered → "answered"
call.completed → "completed"
call.busy → "busy"
call.no_answer → "no_answer"
call.unreachable → "unreachable"
call.rejected → "rejected"
call_initiation_failure → "initiation_failed"
```

### 3. Failure Handling
Automatically extracts and stores failure reasons from:
- `failure_reason` field
- `error_message` field
- `message` field
- Event type (fallback)

### 4. Status Retrieval API
- **Endpoint**: `POST /api/agent-calls/status`
- **Lookup by**: `callSid` OR `conversation_id`
- **Returns**: Current status + raw response

### 5. Database Updates
All webhook events update the `agent_calls` MongoDB collection:
- `status`: Mapped status
- `rawResponse`: Full webhook payload
- `failureReason`: Extracted failure details
- `transcript`: Call transcript (for completed calls)

## Testing the Implementation

### 1. Start the Application
```bash
./gradlew bootRun
```

### 2. Create an Outbound Call
```bash
curl -X POST http://localhost:8080/api/agent-calls \
  -H "Content-Type: application/json" \
  -d '{
    "toNumber": "+6586024972",
    "incidentNumber": "INC123",
    "priority": "1 - Critical",
    "shortDescription": "Test Call",
    "description": "Testing webhook implementation",
    "incidentDateTime": "2026-01-30T10:00:00+08:00",
    "errorDetails": "Test error",
    "possibleFix": "Test fix"
  }'
```

### 3. Simulate Webhook Events (for testing)

**Successful Call:**
```bash
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "conversation_id": "test_conv_123",
    "event_type": "call.completed",
    "status": "completed"
  }'
```

**No Answer:**
```bash
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "event_type": "call.no_answer",
    "status": "failed",
    "failure_reason": "No answer from recipient"
  }'
```

**Busy:**
```bash
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "event_type": "call.busy",
    "status": "failed",
    "failure_reason": "Recipient is busy"
  }'
```

**Unreachable:**
```bash
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "event_type": "call.unreachable",
    "status": "failed",
    "failure_reason": "Recipient unreachable - phone switched off"
  }'
```

### 4. Check Call Status
```bash
curl -X POST http://localhost:8080/api/agent-calls/status \
  -H "Content-Type: application/json" \
  -d '{
    "callSid": "test_call_123"
  }'
```

OR by conversation ID:
```bash
curl -X POST http://localhost:8080/api/agent-calls/status \
  -H "Content-Type: application/json" \
  -d '{
    "conversation_id": "test_conv_123"
  }'
```

### 5. Check MongoDB
```javascript
// View all calls
use alertmind
db.agent_calls.find().pretty()

// View failed calls
db.agent_calls.find({ 
  status: { $in: ["busy", "no_answer", "unreachable", "rejected", "initiation_failed"] } 
}).pretty()

// View specific call
db.agent_calls.findOne({ callId: "test_call_123" })
```

## Expected Logs

When webhook is received:
```
INFO  : === ELEVENLABS CALLBACK RECEIVED ===
INFO  : Full Webhook Payload: {...}
INFO  : Call ID: test_call_123, Conversation ID: test_conv_123, Event Type: call.no_answer, Status: failed
INFO  : Found existing call in database: test_call_123
WARN  : Call failed - Event: call.no_answer, Reason: No answer from recipient
INFO  : Call status updated in database: test_call_123 - no_answer, Event: call.no_answer
```

## Configuration Required

In `application-local.yaml`:
```yaml
ELEVENLABS_AGENT_ID: agent_01k08m7k11e0yspnsnxtye7dct
ELEVENLABS_AGENT_PHONE_NUMBER_ID: phnum_1701k8nv8jx8fs9amj03v3w578ev
ELEVENLABS_STATUS_CALLBACK_URL: "https://your-domain.com/api/webhooks/elevenlabs/call-status"
```

## Swagger UI

View all endpoints: http://localhost:8080/swagger-ui.html

## Next Steps

1. **Deploy to Production**: Set up public HTTPS endpoint for webhooks
2. **Configure Webhooks in ElevenLabs**: Add your callback URL
3. **Test with Real Calls**: Make actual outbound calls and verify webhooks
4. **Monitor**: Watch logs and database for webhook events
5. **Add Alerts**: Set up monitoring for high failure rates

## Files Modified

- ✅ `CallWebhookController.java` - Enhanced with comprehensive event handling
- ✅ `ElevenLabsCallController.java` - Added status retrieval endpoint
- ✅ `application.yaml` - Added all webhook events
- ✅ `CallStatusRequest.java` - New DTO for status queries
- ✅ `CallStatusResponse.java` - New DTO for status responses
- ✅ `WEBHOOK_IMPLEMENTATION.md` - Full documentation

## Build Status

✅ Compilation: SUCCESS
✅ Tests: PASSED
✅ Ready for deployment
