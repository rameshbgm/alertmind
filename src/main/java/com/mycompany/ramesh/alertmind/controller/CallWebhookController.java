package com.mycompany.ramesh.alertmind.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.ramesh.alertmind.entity.AgentCall;
import com.mycompany.ramesh.alertmind.repository.AgentCallRepository;
import com.mycompany.ramesh.alertmind.service.ElevenLabsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/webhooks/elevenlabs")
public class CallWebhookController {

	private static final Logger log = LoggerFactory.getLogger(CallWebhookController.class);
	
	private final AgentCallRepository agentCallRepository;
	private final ElevenLabsClient elevenLabsClient;

	public CallWebhookController(AgentCallRepository agentCallRepository, 
					 ElevenLabsClient elevenLabsClient) {
		this.agentCallRepository = agentCallRepository;
		this.elevenLabsClient = elevenLabsClient;
	}

	@PostMapping("/call-status")
	public Mono<ResponseEntity<String>> handleCallStatus(@RequestBody JsonNode payload) {
		log.info("=== ELEVENLABS CALLBACK RECEIVED ===");
		log.info("Full Webhook Payload: {}", payload);
		
		try {
			// Extract key fields from the payload
			String callId = extractField(payload, "call_id");
			String conversationId = extractField(payload, "conversation_id");
			String eventType = extractField(payload, "event_type");
			String status = extractField(payload, "status");
			
			log.info("Call ID: {}, Conversation ID: {}, Event Type: {}, Status: {}",
					callId, conversationId, eventType, status);

			// Handle different event types as per ElevenLabs documentation
			String mappedStatus = mapEventTypeToStatus(eventType, payload);

			if (callId == null && conversationId == null) {
				log.warn("Webhook received without call_id or conversation_id, ignoring");
				return Mono.just(ResponseEntity.ok("Ignored - no identifiers"));
			}
			
			// Try to find by callId first, then by conversationId
			Mono<AgentCall> callMono = findCallByIdentifiers(callId, conversationId);

			return callMono
				.flatMap(existingCall -> {
					log.info("Found existing call in database: {}", existingCall.callId());

					// Update call with new status
					AgentCall updatedCall = existingCall.withStatusUpdate(
						mappedStatus != null ? mappedStatus : (status != null ? status : existingCall.status()),
						payload
					);

					// Handle failure cases
					if (isFailureEvent(eventType)) {
						String failureReason = extractFailureReason(eventType, payload);
						log.warn("Call failed - Event: {}, Reason: {}", eventType, failureReason);
						updatedCall = updatedCall.withFailure(failureReason);
					}

					// If call is completed/answered successfully, fetch transcript
					if (isCallCompleted(eventType, mappedStatus)) {
						log.info("Call completed successfully, fetching transcript for call: {}", callId);
						return fetchAndSaveTranscript(callId, updatedCall);
					}

					return agentCallRepository.save(updatedCall)
						.doOnSuccess(saved -> log.info("Call status updated in database: {} - {}, Event: {}",
								saved.callId(), saved.status(), eventType))
						.doOnError(error -> log.error("Failed to update call status: {}", error.getMessage()));
				})
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Call not found in database - callId: {}, conversationId: {}", callId, conversationId);
					return Mono.empty();
				}))
				.thenReturn(ResponseEntity.ok("Webhook processed"))
				.onErrorResume(error -> {
					log.error("Error processing webhook: {}", error.getMessage(), error);
					return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
							.body("Error: " + error.getMessage()));
				});

		} catch (Exception e) {
			log.error("Exception processing webhook: {}", e.getMessage(), e);
			return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body("Exception: " + e.getMessage()));
		}
	}
	
	private Mono<AgentCall> findCallByIdentifiers(String callId, String conversationId) {
		if (callId != null && !callId.isBlank()) {
			return agentCallRepository.findByCallId(callId);
		}

		if (conversationId != null && !conversationId.isBlank()) {
			return agentCallRepository.findAll()
					.filter(ac -> ac.rawResponse() != null
							&& ac.rawResponse().has("conversation_id")
							&& conversationId.equals(ac.rawResponse().get("conversation_id").asText()))
					.next();
		}

		return Mono.empty();
	}

	/**
	 * Map ElevenLabs event types to standardized status values
	 * Based on: https://elevenlabs.io/docs/agents-platform/workflows/post-call-webhooks
	 */
	private String mapEventTypeToStatus(String eventType, JsonNode payload) {
		if (eventType == null) return null;

		return switch (eventType) {
			case "call.initiated" -> "initiated";
			case "call.ringing" -> "ringing";
			case "call.answered" -> "answered";
			case "call.completed" -> "completed";
			case "call.ended" -> "ended";

			// Failure events
			case "call_initiation_failure" -> "initiation_failed";
			case "call.busy" -> "busy";
			case "call.no_answer" -> "no_answer";
			case "call.failed" -> "failed";
			case "call.canceled" -> "canceled";
			case "call.unreachable" -> "unreachable";
			case "call.rejected" -> "rejected";

			// Use status field if event type is unknown
			default -> extractField(payload, "status");
		};
	}

	/**
	 * Check if the event represents a failure
	 */
	private boolean isFailureEvent(String eventType) {
		if (eventType == null) return false;

		return eventType.equals("call_initiation_failure") ||
			   eventType.equals("call.busy") ||
			   eventType.equals("call.no_answer") ||
			   eventType.equals("call.failed") ||
			   eventType.equals("call.canceled") ||
			   eventType.equals("call.unreachable") ||
			   eventType.equals("call.rejected");
	}

	/**
	 * Extract failure reason from webhook payload
	 */
	private String extractFailureReason(String eventType, JsonNode payload) {
		// Try to get explicit failure_reason field
		if (payload.has("failure_reason") && !payload.get("failure_reason").isNull()) {
			return payload.get("failure_reason").asText();
		}

		// Try to get error_message field
		if (payload.has("error_message") && !payload.get("error_message").isNull()) {
			return payload.get("error_message").asText();
		}

		// Try to get message field
		if (payload.has("message") && !payload.get("message").isNull()) {
			return payload.get("message").asText();
		}

		// Fallback to event type as reason
		return switch (eventType) {
			case "call_initiation_failure" -> "Call initiation failed";
			case "call.busy" -> "Recipient is busy";
			case "call.no_answer" -> "No answer from recipient";
			case "call.unreachable" -> "Recipient unreachable";
			case "call.rejected" -> "Call rejected by recipient";
			case "call.canceled" -> "Call canceled";
			default -> "Call failed - " + eventType;
		};
	}

	private String extractField(JsonNode payload, String fieldName) {
		if (payload.has(fieldName) && !payload.get(fieldName).isNull()) {
			return payload.get(fieldName).asText();
		}
		return null;
	}
	
	private boolean isCallCompleted(String eventType, String status) {
		if (eventType == null) {
			return false;
		}
		// Consider call completed if event is call.completed or call.answered with completed status
		return eventType.equals("call.completed") || 
			   (eventType.equals("call.answered") && "completed".equalsIgnoreCase(status));
	}
	
	private Mono<AgentCall> fetchAndSaveTranscript(String callId, AgentCall call) {
		return elevenLabsClient.getCallTranscript(callId)
				.flatMap(transcript -> {
					log.info("=== TRANSCRIPT RETRIEVED ===");
					log.info("Call ID: {}", callId);
					log.info("Transcript: {}", transcript);
					
					AgentCall updatedCall = call.withTranscript(transcript);

					return agentCallRepository.save(updatedCall)
						.doOnSuccess(saved -> log.info("Transcript saved for call: {}", saved.callId()))
						.doOnError(error -> log.error("Failed to save transcript: {}", error.getMessage()));
				})
				.onErrorResume(error -> {
					log.error("Failed to fetch transcript for call {}: {}", callId, error.getMessage());
					// Still save the call without transcript
					return agentCallRepository.save(call);
				});
	}
	
}
