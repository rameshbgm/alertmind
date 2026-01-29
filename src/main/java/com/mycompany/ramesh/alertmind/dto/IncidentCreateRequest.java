package com.mycompany.ramesh.alertmind.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record IncidentCreateRequest(
		@NotBlank String incidentNumber,
		@NotBlank String shortDescription,
		String longDescription,
		@NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime incidentDateTime,
		@NotBlank String assignmentGroup,
		@NotNull @Valid ContactDetails rosterContact,
		@NotNull @Valid ContactDetails escalation
) {
}
