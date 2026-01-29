package com.mycompany.ramesh.alertmind.controller;

import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallRequest;
import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallResponse;
import com.mycompany.ramesh.alertmind.entity.AgentCall;
import com.mycompany.ramesh.alertmind.repository.AgentCallRepository;
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

@RestController
@RequestMapping("/api/agent-calls")
public class ElevenLabsCallController {

	private static final Logger log = LoggerFactory.getLogger(ElevenLabsCallController.class);

	private final ElevenLabsClient elevenLabsClient;
	private final AgentCallRepository agentCallRepository;

	public ElevenLabsCallController(ElevenLabsClient elevenLabsClient,
								AgentCallRepository agentCallRepository) {
		this.elevenLabsClient = elevenLabsClient;
		this.agentCallRepository = agentCallRepository;
	}

	@PostMapping
	public Mono<ResponseEntity<CreateOutboundCallResponse>> createCall(
			@Valid @RequestBody CreateOutboundCallRequest request) {
		log.info("=== CREATE OUTBOUND CALL REQUEST ===");
		log.info("To Number: {}", request.toNumber());
		log.info("Incident Number: {}", request.incidentNumber());
		log.info("Priority: {}", request.priority());
		log.info("Short Description: {}", request.shortDescription());
		log.info("Full Request: {}", request);
		
		return elevenLabsClient.createOutboundCall(request)
				.flatMap(response -> {
					log.info("=== CREATE OUTBOUND CALL RESPONSE ===");
					log.info("Call ID: {}", response.callId());
					log.info("Raw Response: {}", response.rawResponse());
					
					AgentCall call = AgentCall.from(
							response.callId(),
							elevenLabsClient.getAgentId(),
							elevenLabsClient.getAgentPhoneNumberId(),
							request.toNumber(),
							elevenLabsClient.extractCallStatus(response.rawResponse()),
							response.rawResponse()
					);
					return agentCallRepository.save(call)
							.doOnSuccess(saved -> log.info("Call saved to database with ID: {}, callId: {}", 
									saved.id(), saved.callId()))
							.doOnError(error -> log.error("Failed to save call to database: {}", error.getMessage()))
							.thenReturn(response);
				})
				.map(ResponseEntity::ok);
	}
}
