package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateElevenLabsAgentResponse(
		String agentId,
		JsonNode rawResponse
) {
}
