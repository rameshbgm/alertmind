package com.mycompany.ramesh.alertmind.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ContactDetails(
		@NotBlank String phoneNumber,
		@NotBlank @Email String email,
		@Min(1) int callSequence
) {
}
