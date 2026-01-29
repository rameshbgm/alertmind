package com.mycompany.ramesh.alertmind.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(
		@NotBlank String accountSid,
		@NotBlank String authToken,
		@NotBlank String fromNumber
) {
}
