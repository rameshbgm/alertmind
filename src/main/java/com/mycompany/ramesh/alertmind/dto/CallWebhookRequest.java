package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record CallWebhookRequest(
		@JsonProperty("call_id") String callId,
		@JsonProperty("event_type") String eventType,
		String status,
		@JsonProperty("phone_number") PhoneNumberInfo phoneNumber,
		@JsonProperty("agent_id") String agentId,
		@JsonProperty("call_duration") Integer callDuration,
		@JsonProperty("failure_reason") String failureReason,
		JsonNode metadata,
		@JsonProperty("raw_data") JsonNode rawData
) {
	public record PhoneNumberInfo(
			String number,
			String type
	) {}
}
