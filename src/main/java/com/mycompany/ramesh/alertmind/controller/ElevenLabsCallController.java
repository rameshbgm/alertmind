package com.mycompany.ramesh.alertmind.controller;

import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallRequest;
import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallResponse;
import com.mycompany.ramesh.alertmind.entity.AgentCall;
import com.mycompany.ramesh.alertmind.repository.AgentCallRepository;
import com.mycompany.ramesh.alertmind.service.ElevenLabsClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent-calls")
public class ElevenLabsCallController {

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
		return elevenLabsClient.createOutboundCall(request)
				.flatMap(response -> {
					AgentCall call = AgentCall.from(
							response.callId(),
							elevenLabsClient.getAgentId(),
							elevenLabsClient.getAgentPhoneNumberId(),
							request.toNumber(),
							elevenLabsClient.extractCallStatus(response.rawResponse()),
							response.rawResponse()
					);
					return agentCallRepository.save(call).thenReturn(response);
				})
				.map(ResponseEntity::ok);
	}
}
