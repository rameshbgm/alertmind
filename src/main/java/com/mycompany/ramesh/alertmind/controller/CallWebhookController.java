package com.mycompany.ramesh.alertmind.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.ramesh.alertmind.dto.CallWebhookRequest;
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
			String eventType = extractField(payload, "event_type");
			String status = extractField(payload, "status");
			
			log.info("Call ID: {}, Event Type: {}, Status: {}", callId, eventType, status);
			
			if (callId == null) {
				log.warn("Webhook received without call_id, ignoring");
				return Mono.just(ResponseEntity.ok("Ignored - no call_id"));
			}
			
			return agentCallRepository.findByCallId(callId)
					.flatMap(existingCall -> {
						log.info("Found existing call in database: {}", existingCall.callId());
						
						// Update call with new status
						AgentCall updatedCall = existingCall.withStatusUpdate(
								status != null ? status : existingCall.status(),
								eventType,
								payload
						);
						
						// If there's a failure reason, add it
						if (payload.has("failure_reason") && !payload.get("failure_reason").isNull()) {
							String failureReason = payload.get("failure_reason").asText();
							log.warn("Call failed with reason: {}", failureReason);
							updatedCall = updatedCall.withFailure(failureReason);
						}
						
						// If call is completed/answered successfully, fetch transcript
						if (isCallCompleted(eventType, status)) {
							log.info("Call completed successfully, fetching transcript for call: {}", callId);
							return fetchAndSaveTranscript(callId, updatedCall);
						}
						
						return agentCallRepository.save(updatedCall)
								.doOnSuccess(saved -> log.info("Call status updated in database: {} - {}", 
										saved.callId(), saved.status()))
								.doOnError(error -> log.error("Failed to update call status: {}", error.getMessage()));
					})
					.switchIfEmpty(Mono.defer(() -> {
						log.warn("Call not found in database: {}", callId);
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
					
					// Extract duration if available
					Integer duration = transcript.has("duration") && !transcript.get("duration").isNull() 
							? transcript.get("duration").asInt() 
							: null;
					
					// Build transcript text from messages
					String transcriptText = buildTranscriptText(transcript);
					
					AgentCall updatedCall = call.withTranscript(transcriptText, duration);
					
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
	
	private String buildTranscriptText(JsonNode transcript) {
		if (!transcript.has("messages") || transcript.get("messages").isNull()) {
			return transcript.toString();
		}
		
		StringBuilder sb = new StringBuilder();
		transcript.get("messages").forEach(message -> {
			if (message.has("role") && message.has("message")) {
				String role = message.get("role").asText();
				String text = message.get("message").asText();
				sb.append("[").append(role).append("] ").append(text).append("\n");
			}
		});
		
		return sb.length() > 0 ? sb.toString() : transcript.toString();
	}
}
