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
		JsonNode requestPayload,
		JsonNode rawResponse,
		JsonNode transcript,
		String failureReason,
		Instant createdAt
) {
	public static AgentCall fromRequest(String toNumber, JsonNode requestPayload) {
		return new AgentCall(null, null, null, null, toNumber, "created", requestPayload, null, null, null, Instant.now());
	}

	public static AgentCall fromResponse(String callId,
							 String agentId,
							 String agentPhoneNumberId,
							 String toNumber,
							 String status,
							 JsonNode rawResponse) {
		return new AgentCall(null, callId, agentId, agentPhoneNumberId, toNumber, status, null, rawResponse, null, null, Instant.now());
	}

	public AgentCall withUpdatedResponse(String callId, String agentId, String agentPhoneNumberId, String status, JsonNode rawResponse) {
		return new AgentCall(this.id, callId, agentId, agentPhoneNumberId, this.toNumber, status, this.requestPayload, rawResponse, this.transcript, this.failureReason, this.createdAt);
	}

	public AgentCall withTranscript(JsonNode transcript) {
		return new AgentCall(this.id, this.callId, this.agentId, this.agentPhoneNumberId, this.toNumber, this.status, this.requestPayload, this.rawResponse, transcript, this.failureReason, this.createdAt);
	}

	public AgentCall withStatusUpdate(String status, JsonNode rawResponse) {
		return new AgentCall(this.id, this.callId, this.agentId, this.agentPhoneNumberId, this.toNumber, status, this.requestPayload, rawResponse, this.transcript, this.failureReason, this.createdAt);
	}

	public AgentCall withFailure(String failureReason) {
		return new AgentCall(this.id, this.callId, this.agentId, this.agentPhoneNumberId, this.toNumber, "failed", this.requestPayload, this.rawResponse, this.transcript, failureReason, this.createdAt);
	}
}
