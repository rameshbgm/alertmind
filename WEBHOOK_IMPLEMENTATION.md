# ElevenLabs Webhook Events Implementation

## Overview
This document describes the implementation of ElevenLabs post-call webhooks as per the official documentation:
https://elevenlabs.io/docs/agents-platform/workflows/post-call-webhooks

## Supported Webhook Events

### Success Events
| Event Type | Status Mapped | Description |
|------------|---------------|-------------|
| `call.initiated` | `initiated` | Call has been initiated |
| `call.ringing` | `ringing` | Call is ringing at recipient |
| `call.answered` | `answered` | Call was answered by recipient |
| `call.completed` | `completed` | Call completed successfully |
| `call.ended` | `ended` | Call has ended |

### Failure Events
| Event Type | Status Mapped | Description |
|------------|---------------|-------------|
| `call_initiation_failure` | `initiation_failed` | Call failed to initiate |
| `call.busy` | `busy` | Recipient is busy |
| `call.no_answer` | `no_answer` | No answer from recipient |
| `call.failed` | `failed` | General call failure |
| `call.canceled` | `canceled` | Call was canceled |
| `call.unreachable` | `unreachable` | Recipient unreachable (switched off, no signal) |
| `call.rejected` | `rejected` | Call rejected by recipient |

## Webhook Endpoint

**URL:** `POST /api/webhooks/elevenlabs/call-status`

**Authentication:** None (configure IP whitelist in production)

### Webhook Payload Example

```json
{
  "call_id": "CAaaf497febbe85ab7ab20ec32b3808caa",
  "conversation_id": "conv_8001kg64z481ege91gcvyrxngwrs",
  "event_type": "call.completed",
  "status": "completed",
  "failure_reason": null,
  "timestamp": "2026-01-30T08:00:00Z"
}
```

### Failure Payload Example

```json
{
  "call_id": "CAaaf497febbe85ab7ab20ec32b3808caa",
  "conversation_id": "conv_8001kg64z481ege91gcvyrxngwrs",
  "event_type": "call.no_answer",
  "status": "failed",
  "failure_reason": "No answer from recipient",
  "error_message": "The recipient did not answer within the timeout period",
  "timestamp": "2026-01-30T08:00:00Z"
}
```

## Database Schema

The `agent_calls` collection stores:

```javascript
{
  "_id": ObjectId("..."),
  "callId": "CAaaf497febbe85ab7ab20ec32b3808caa",
  "agentId": "agent_01k08m7k11e0yspnsnxtye7dct",
  "agentPhoneNumberId": "phnum_1701k8nv8jx8fs9amj03v3w578ev",
  "toNumber": "+6586024972",
  "status": "no_answer",
  "requestPayload": { ... },  // Original request
  "rawResponse": { ... },     // Latest webhook payload
  "transcript": { ... },      // Call transcript (if completed)
  "failureReason": "No answer from recipient",
  "createdAt": ISODate("2026-01-30T08:00:00Z")
}
```

## Status Retrieval API

**Endpoint:** `POST /api/agent-calls/status`

**Request Body:**
```json
{
  "conversation_id": "conv_8001kg64z481ege91gcvyrxngwrs",
  "callSid": "CAaaf497febbe85ab7ab20ec32b3808caa"
}
```

**Response:**
```json
{
  "callId": "CAaaf497febbe85ab7ab20ec32b3808caa",
  "status": "no_answer",
  "rawResponse": {
    "call_id": "CAaaf497febbe85ab7ab20ec32b3808caa",
    "conversation_id": "conv_8001kg64z481ege91gcvyrxngwrs",
    "event_type": "call.no_answer",
    "status": "failed",
    "failure_reason": "No answer from recipient"
  }
}
```

## Configuration

Add to `application-local.yaml`:

```yaml
ELEVENLABS_STATUS_CALLBACK_URL: "https://your-domain.com/api/webhooks/elevenlabs/call-status"
```

The following events are automatically registered:
- call.initiated
- call.ringing
- call.answered
- call.completed
- call.ended
- call_initiation_failure
- call.busy
- call.no_answer
- call.failed
- call.canceled
- call.unreachable
- call.rejected

## Implementation Details

### Webhook Handler Logic

1. **Receive Webhook**: Accept POST request with JSON payload
2. **Extract Identifiers**: Get `call_id` and `conversation_id`
3. **Find Record**: Look up call in MongoDB by identifiers
4. **Map Status**: Convert event_type to standardized status
5. **Handle Failures**: Extract failure reason for failed calls
6. **Update Database**: Save updated status and raw response
7. **Fetch Transcript**: If call completed, fetch and save transcript
8. **Log Everything**: Comprehensive logging at each step

### Failure Reason Extraction Priority

1. `failure_reason` field (primary)
2. `error_message` field (secondary)
3. `message` field (tertiary)
4. Event type mapping (fallback)

### Status Lookup Logic

1. Check database for cached status
2. If not found or stale, query ElevenLabs API
3. Update database with latest status
4. Return status to caller

## Testing

### Test Webhook Locally

```bash
# Simulate successful call
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "conversation_id": "test_conv_123",
    "event_type": "call.completed",
    "status": "completed"
  }'

# Simulate no answer
curl -X POST http://localhost:8080/api/webhooks/elevenlabs/call-status \
  -H "Content-Type: application/json" \
  -d '{
    "call_id": "test_call_123",
    "event_type": "call.no_answer",
    "status": "failed",
    "failure_reason": "No answer from recipient"
  }'
```

### Test Status Retrieval

```bash
curl -X POST http://localhost:8080/api/agent-calls/status \
  -H "Content-Type: application/json" \
  -d '{
    "callSid": "test_call_123"
  }'
```

## Production Checklist

- [ ] Configure public HTTPS endpoint for webhooks
- [ ] Set `ELEVENLABS_STATUS_CALLBACK_URL` in environment
- [ ] Implement IP whitelist for webhook endpoint
- [ ] Set up monitoring for failed webhooks
- [ ] Configure retry logic for failed status updates
- [ ] Set up alerts for high failure rates
- [ ] Document escalation procedures

## Monitoring Queries

### Check failed calls
```javascript
db.agent_calls.find({ 
  status: { $in: ["busy", "no_answer", "failed", "unreachable", "rejected", "initiation_failed"] } 
})
```

### Failure rate by reason
```javascript
db.agent_calls.aggregate([
  { $match: { failureReason: { $ne: null } } },
  { $group: { _id: "$failureReason", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
])
```

### Recent call status distribution
```javascript
db.agent_calls.aggregate([
  { $match: { createdAt: { $gte: new Date(Date.now() - 24*60*60*1000) } } },
  { $group: { _id: "$status", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
])
```
