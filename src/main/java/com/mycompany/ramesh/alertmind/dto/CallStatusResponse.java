package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CallStatusResponse(
        String callId,
        String status,
        JsonNode rawResponse
) {
}
