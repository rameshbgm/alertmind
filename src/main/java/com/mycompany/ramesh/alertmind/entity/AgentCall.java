package com.mycompany.ramesh.alertmind.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "agent_calls")
public record AgentCall(
		@Id String id,
		@Indexed(unique = true) String callId,
		String agentId,
		String agentPhoneNumberId,
		String toNumber,
		String status,
		JsonNode rawResponse,
		Instant createdAt,
		Instant statusUpdatedAt,
		String callTranscript,
		Integer callDurationSeconds,
		String failureReason,
		String lastCallbackEvent
) {
	public static AgentCall from(String callId,
								 String agentId,
								 String agentPhoneNumberId,
								 String toNumber,
								 String status,
								 JsonNode rawResponse) {
		return new AgentCall(null, callId, agentId, agentPhoneNumberId, toNumber,
				status, rawResponse, Instant.now(), null, null, null, null, null);
	}
	
	public AgentCall withStatusUpdate(String newStatus, String callbackEvent, JsonNode rawResponse) {
		return new AgentCall(id, callId, agentId, agentPhoneNumberId, toNumber,
				newStatus, rawResponse, createdAt, Instant.now(), callTranscript, 
				callDurationSeconds, failureReason, callbackEvent);
	}
	
	public AgentCall withTranscript(String transcript, Integer durationSeconds) {
		return new AgentCall(id, callId, agentId, agentPhoneNumberId, toNumber,
				status, rawResponse, createdAt, statusUpdatedAt, transcript, 
				durationSeconds, failureReason, lastCallbackEvent);
	}
	
	public AgentCall withFailure(String reason) {
		return new AgentCall(id, callId, agentId, agentPhoneNumberId, toNumber,
				status, rawResponse, createdAt, statusUpdatedAt, callTranscript, 
				callDurationSeconds, reason, lastCallbackEvent);
	}
}
