package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateOutboundCallResponse(
		String callId,
		JsonNode rawResponse
) {
}
