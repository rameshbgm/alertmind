package com.mycompany.ramesh.alertmind.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.elevenlabs")
public record ElevenLabsProperties(
		@NotBlank String baseUrl,
		@NotBlank String apiKey,
		@NotBlank String agentsPath,
		@NotBlank String agentName,
		@NotBlank String voiceId,
		@NotBlank String language,
		@NotNull Resource systemPromptFile,
		@NotNull Resource firstMessageFile
) {
}
