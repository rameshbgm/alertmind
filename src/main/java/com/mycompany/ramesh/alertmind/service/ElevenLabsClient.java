package com.mycompany.ramesh.alertmind.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.ramesh.alertmind.config.ElevenLabsProperties;
import com.mycompany.ramesh.alertmind.dto.CreateElevenLabsAgentRequest;
import com.mycompany.ramesh.alertmind.dto.CreateElevenLabsAgentResponse;
import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallRequest;
import com.mycompany.ramesh.alertmind.dto.CreateOutboundCallResponse;
import com.mycompany.ramesh.alertmind.dto.IncidentCreateRequest;
import com.mycompany.ramesh.alertmind.exception.UpstreamServiceException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ElevenLabsClient {

	private static final Logger log = LoggerFactory.getLogger(ElevenLabsClient.class);

	private final WebClient webClient;
	private final ElevenLabsProperties properties;
	private String systemPromptTemplate;
	private String firstMessageTemplate;

	public ElevenLabsClient(WebClient elevenLabsWebClient,
						ElevenLabsProperties properties) {
		this.webClient = elevenLabsWebClient;
		this.properties = properties;
	}

	@PostConstruct
	public void init() throws IOException {
		this.systemPromptTemplate = properties.systemPromptFile()
				.getContentAsString(StandardCharsets.UTF_8);
		this.firstMessageTemplate = properties.firstMessageFile()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	public Mono<CreateElevenLabsAgentResponse> createAgent(CreateElevenLabsAgentRequest request) {
		var payload = ElevenLabsAgentCreatePayload.from(request, properties, systemPromptTemplate, firstMessageTemplate);
		log.info("Creating ElevenLabs agent with name: {}", request.name());
		log.debug("Request payload: {}", payload);

		return webClient.post()
				.uri(properties.agentsPath())
				.bodyValue(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, response ->
					response.bodyToMono(String.class)
						.defaultIfEmpty("")
						.flatMap(errorBody -> {
							log.error("ElevenLabs API error - Status: {}, Body: {}",
									response.statusCode(), errorBody);
							return Mono.error(new UpstreamServiceException(
									response.statusCode(), errorBody));
						}))
				.bodyToMono(JsonNode.class)
				.doOnSuccess(response -> log.info("Successfully created agent: {}", extractAgentId(response)))
				.doOnError(error -> log.error("Failed to create agent: {}", error.getMessage()))
				.map(response -> new CreateElevenLabsAgentResponse(extractAgentId(response), response));
	}

	public Mono<CreateElevenLabsAgentResponse> createAgentForIncident(IncidentCreateRequest incident) {
		String systemPrompt = replaceIncidentPlaceholders(systemPromptTemplate, incident);
		String firstMessage = replaceIncidentPlaceholders(firstMessageTemplate, incident);

		var request = new CreateElevenLabsAgentRequest(
				properties.agentName() + " - " + incident.incidentNumber(),
				"Incident callout agent for " + incident.incidentNumber(),
				properties.voiceId(),
				properties.language(),
				firstMessage,
				systemPrompt
		);
		return createAgent(request);
	}

	private String replaceIncidentPlaceholders(String template, IncidentCreateRequest incident) {
		return template
				.replace("{{incident_number}}", incident.incidentNumber())
				.replace("{{short_description}}", incident.shortDescription())
				.replace("{{description}}", incident.longDescription() != null ? incident.longDescription() : "")
				.replace("{{priority}}", "High")
				.replace("{{incident_date_time}}", incident.incidentDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.replace("{{error_details}}", incident.longDescription() != null ? incident.longDescription() : "")
				.replace("{{possible_fix}}", "Please check IT Assist for details");
	}

	public Mono<Void> deleteAgent(String agentId) {
		log.info("Deleting ElevenLabs agent: {}", agentId);
		return webClient.delete()
				.uri(uriBuilder -> uriBuilder.path(properties.agentsPath()).path("/{agentId}")
						.build(agentId))
				.retrieve()
				.onStatus(HttpStatusCode::isError, response ->
					response.bodyToMono(String.class)
						.defaultIfEmpty("")
						.flatMap(errorBody -> {
							log.error("ElevenLabs API error deleting agent {} - Status: {}, Body: {}",
									agentId, response.statusCode(), errorBody);
							return Mono.error(new UpstreamServiceException(
									response.statusCode(), errorBody));
						}))
				.bodyToMono(Void.class)
				.doOnSuccess(v -> log.info("Successfully deleted agent: {}", agentId))
				.doOnError(error -> log.error("Failed to delete agent {}: {}", agentId, error.getMessage()));
	}

	public Mono<CreateOutboundCallResponse> createOutboundCall(CreateOutboundCallRequest request) {
		var payload = ElevenLabsOutboundCallPayload.from(request, properties);
		log.info("Creating ElevenLabs outbound call to: {}", request.toNumber());

		return webClient.post()
				.uri(properties.callsPath())
				.bodyValue(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, response ->
					response.bodyToMono(String.class)
						.defaultIfEmpty("")
						.flatMap(errorBody -> {
							log.error("ElevenLabs call API error - Status: {}, Body: {}",
									response.statusCode(), errorBody);
							return Mono.error(new UpstreamServiceException(
									response.statusCode(), errorBody));
						}))
				.bodyToMono(JsonNode.class)
				.doOnSuccess(response -> log.info("Successfully created call: {}", extractCallId(response)))
				.doOnError(error -> log.error("Failed to create call: {}", error.getMessage()))
				.map(response -> new CreateOutboundCallResponse(extractCallId(response), response));
	}

	public String getAgentId() {
		return properties.agentId();
	}

	public String getAgentPhoneNumberId() {
		return properties.agentPhoneNumberId();
	}

	public String extractCallStatus(JsonNode response) {
		if (response == null) {
			return null;
		}
		if (response.hasNonNull("status")) {
			return response.get("status").asText();
		}
		return null;
	}

	private static String extractAgentId(JsonNode response) {
		if (response == null) {
			return null;
		}
		if (response.hasNonNull("agent_id")) {
			return response.get("agent_id").asText();
		}
		if (response.hasNonNull("id")) {
			return response.get("id").asText();
		}
		return null;
	}

	private static String extractCallId(JsonNode response) {
		if (response == null) {
			return null;
		}
		if (response.hasNonNull("call_id")) {
			return response.get("call_id").asText();
		}
		if (response.hasNonNull("id")) {
			return response.get("id").asText();
		}
		return null;
	}

	private record ElevenLabsAgentCreatePayload(
			String name,
			@JsonProperty("conversation_config") ConversationConfig conversationConfig
	) {
		private static ElevenLabsAgentCreatePayload from(CreateElevenLabsAgentRequest request,
											ElevenLabsProperties props,
											String defaultSystemPrompt,
											String defaultFirstMessage) {
			return new ElevenLabsAgentCreatePayload(
					request.name(),
					new ConversationConfig(
							new Agent(
									new Prompt(request.systemPrompt() != null ? request.systemPrompt() : defaultSystemPrompt),
									request.firstMessage() != null ? request.firstMessage() : defaultFirstMessage,
									request.language() != null ? request.language() : props.language()
							),
							new Tts(request.voiceId() != null ? request.voiceId() : props.voiceId())
					)
			);
		}
	}

	private record ConversationConfig(
			Agent agent,
			Tts tts
	) {}

	private record Agent(
			Prompt prompt,
			@JsonProperty("first_message") String firstMessage,
			String language
	) {}

	private record Prompt(
			String prompt
	) {}

	private record Tts(
			@JsonProperty("voice_id") String voiceId
	) {}

	private record ElevenLabsOutboundCallPayload(
			@JsonProperty("agent_id") String agentId,
			@JsonProperty("agent_phone_number_id") String agentPhoneNumberId,
			@JsonProperty("to_number") String toNumber,
			@JsonProperty("dynamic_variables") Map<String, Object> dynamicVariables,
			@JsonProperty("status_callback_url") String statusCallbackUrl,
			@JsonProperty("status_callback_events") List<String> statusCallbackEvents
	) {
		private static ElevenLabsOutboundCallPayload from(CreateOutboundCallRequest request,
											ElevenLabsProperties props) {
			java.util.Map<String, Object> dynamicVariables = new java.util.LinkedHashMap<>();
			dynamicVariables.put("incident_number", request.incidentNumber());
			dynamicVariables.put("priority", request.priority());
			dynamicVariables.put("short_description", request.shortDescription());
			if (request.description() != null) dynamicVariables.put("description", request.description());
			if (request.incidentDateTime() != null) dynamicVariables.put("incident_date_time", request.incidentDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			if (request.errorDetails() != null) dynamicVariables.put("error_details", request.errorDetails());
			if (request.possibleFix() != null) dynamicVariables.put("possible_fix", request.possibleFix());

			return new ElevenLabsOutboundCallPayload(
					props.agentId(),
					props.agentPhoneNumberId(),
					request.toNumber(),
					dynamicVariables,
					props.statusCallbackUrl(),
					props.statusCallbackEvents()
			);
		}
	}
}
