package com.mycompany.ramesh.alertmind.dto;

import java.time.OffsetDateTime;

public record IncidentCreateResponse(
		String requestId,
		OffsetDateTime receivedAt,
		String agentId
) {
}
