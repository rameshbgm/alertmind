package com.mycompany.ramesh.alertmind.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateElevenLabsAgentRequest(
		@NotBlank String name,
		String description,
		@NotBlank String voiceId,
		@NotBlank String language,
		String firstMessage,
		String systemPrompt
) {
}
