package com.mycompany.ramesh.alertmind.dto;

import jakarta.validation.constraints.NotBlank;

public record CallStatusRequest(
        String conversation_id,
        String callSid
) {
}
