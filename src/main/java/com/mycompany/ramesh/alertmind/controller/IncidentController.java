package com.mycompany.ramesh.alertmind.controller;

import com.mycompany.ramesh.alertmind.dto.IncidentCreateRequest;
import com.mycompany.ramesh.alertmind.dto.IncidentCreateResponse;
import com.mycompany.ramesh.alertmind.service.ElevenLabsClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

	private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

	private final ElevenLabsClient elevenLabsClient;

	public IncidentController(ElevenLabsClient elevenLabsClient) {
		this.elevenLabsClient = elevenLabsClient;
	}

	@PostMapping
	public Mono<ResponseEntity<IncidentCreateResponse>> createIncident(
			@Valid @RequestBody IncidentCreateRequest request) {
		String requestId = UUID.randomUUID().toString();
		log.info("Received incident: {} - {}", request.incidentNumber(), request.shortDescription());

		return elevenLabsClient.createAgentForIncident(request)
				.doOnSuccess(agentResponse -> log.info("Created ElevenLabs agent: {} for incident: {}",
						agentResponse.agentId(), request.incidentNumber()))
				.doOnError(error -> log.error("Failed to create ElevenLabs agent for incident: {}",
						request.incidentNumber(), error))
				.map(agentResponse -> {
					var response = new IncidentCreateResponse(requestId, OffsetDateTime.now(), agentResponse.agentId());
					return ResponseEntity.accepted().body(response);
				})
				.onErrorResume(error -> {
					var response = new IncidentCreateResponse(requestId, OffsetDateTime.now(), null);
					return Mono.just(ResponseEntity.accepted().body(response));
				});
	}
}
