package com.mycompany.ramesh.alertmind.controller;

import com.mycompany.ramesh.alertmind.dto.CreateElevenLabsAgentRequest;
import com.mycompany.ramesh.alertmind.dto.CreateElevenLabsAgentResponse;
import com.mycompany.ramesh.alertmind.entity.Agent;
import com.mycompany.ramesh.alertmind.repository.AgentRepository;
import com.mycompany.ramesh.alertmind.service.ElevenLabsClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agents")
public class ElevenLabsAgentController {

	private final ElevenLabsClient elevenLabsClient;
	private final AgentRepository agentRepository;

	public ElevenLabsAgentController(ElevenLabsClient elevenLabsClient, AgentRepository agentRepository) {
		this.elevenLabsClient = elevenLabsClient;
		this.agentRepository = agentRepository;
	}

	@PostMapping
	public Mono<ResponseEntity<CreateElevenLabsAgentResponse>> createAgent(
			@Valid @RequestBody CreateElevenLabsAgentRequest request) {
		return elevenLabsClient.createAgent(request)
				.flatMap(response -> {
					Agent agent = Agent.from(
							response.agentId(),
							request.name(),
							request.description(),
							request.voiceId(),
							request.language(),
							request.firstMessage(),
							request.systemPrompt(),
							response.rawResponse()
					);
					return agentRepository.save(agent).thenReturn(response);
				})
				.map(ResponseEntity::ok);
	}

	@GetMapping
	public Flux<Agent> getAllAgents() {
		return agentRepository.findAll();
	}

	@GetMapping("/{agentId}")
	public Mono<ResponseEntity<Agent>> getAgent(@PathVariable String agentId) {
		return agentRepository.findByAgentId(agentId)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{agentId}")
	public Mono<ResponseEntity<Void>> deleteAgent(@PathVariable String agentId) {
		return elevenLabsClient.deleteAgent(agentId)
				.then(agentRepository.deleteByAgentId(agentId))
				.thenReturn(ResponseEntity.noContent().build());
	}
}
