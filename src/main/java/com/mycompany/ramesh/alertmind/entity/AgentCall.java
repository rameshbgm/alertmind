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
		Instant createdAt
) {
	public static AgentCall from(String callId,
								 String agentId,
								 String agentPhoneNumberId,
								 String toNumber,
								 String status,
								 JsonNode rawResponse) {
		return new AgentCall(null, callId, agentId, agentPhoneNumberId, toNumber,
				status, rawResponse, Instant.now());
	}
}
