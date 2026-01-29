package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateOutboundCallRequest(
        @NotBlank String toNumber,
        @NotBlank String incidentNumber,
        @NotBlank String priority,
        @NotBlank String shortDescription,
        String description,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime incidentDateTime,
        String errorDetails,
        String possibleFix
) {
}
