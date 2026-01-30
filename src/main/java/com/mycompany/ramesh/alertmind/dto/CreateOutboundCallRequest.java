package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOutboundCallRequest(
        @NotBlank String toNumber,
        @NotBlank String incidentNumber,
        @NotBlank String priority,
        @NotBlank String shortDescription,
        String description,
        @NotBlank String incidentDateTime,
        String errorDetails,
        String possibleFix
) {
}
