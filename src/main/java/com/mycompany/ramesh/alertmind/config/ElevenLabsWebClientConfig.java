package com.mycompany.ramesh.alertmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ElevenLabsWebClientConfig {

	@Bean
	public WebClient elevenLabsWebClient(ElevenLabsProperties properties) {
		return WebClient.builder()
				.baseUrl(properties.baseUrl())
				.defaultHeader("xi-api-key", properties.apiKey())
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}
}
