package com.mycompany.ramesh.alertmind.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.ramesh.alertmind.dto.CallStatusRequest;
import com.mycompany.ramesh.alertmind.dto.CallStatusResponse;
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
    private final ObjectMapper objectMapper;

    public ElevenLabsCallController(ElevenLabsClient elevenLabsClient,
                                   AgentCallRepository agentCallRepository,
                                   ObjectMapper objectMapper) {
        this.elevenLabsClient = elevenLabsClient;
        this.agentCallRepository = agentCallRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public Mono<ResponseEntity<CreateOutboundCallResponse>> createCall(
            @Valid @RequestBody CreateOutboundCallRequest request) {
        // 1) Log request and save request payload to DB
        log.info("Received createCall request to {} for incident {}", request.toNumber(), request.incidentNumber());
        JsonNode requestJson = objectMapper.valueToTree(request);
        AgentCall requestRecord = AgentCall.fromRequest(request.toNumber(), requestJson);

        return agentCallRepository.save(requestRecord)
                .flatMap(savedRequest ->
                        // 2) Call ElevenLabs
                        elevenLabsClient.createOutboundCall(request)
                                .flatMap(apiResponse -> {
                                    // Save response to DB
                                    AgentCall responseRecord = savedRequest.withUpdatedResponse(
                                            apiResponse.callId(),
                                            elevenLabsClient.getAgentId(),
                                            elevenLabsClient.getAgentPhoneNumberId(),
                                            elevenLabsClient.extractCallStatus(apiResponse.rawResponse()),
                                            apiResponse.rawResponse());

                                    return agentCallRepository.save(responseRecord)
                                            .flatMap(savedResponse -> {
                                                // 3) If successful, fetch transcript and save
                                                String status = elevenLabsClient.extractCallStatus(apiResponse.rawResponse());
                                                if ("completed".equalsIgnoreCase(status) || "answered".equalsIgnoreCase(status)) {
                                                    return elevenLabsClient.getCallTranscript(apiResponse.callId())
                                                            .flatMap(transcriptJson -> {
                                                                AgentCall withTranscript = savedResponse.withTranscript(transcriptJson);
                                                                return agentCallRepository.save(withTranscript)
                                                                        .thenReturn(apiResponse);
                                                            })
                                                            .onErrorResume(e -> {
                                                                log.error("Failed to fetch transcript for call {}: {}", apiResponse.callId(), e.getMessage());
                                                                return Mono.just(apiResponse);
                                                            });
                                                } else {
                                                    return Mono.just(apiResponse);
                                                }
                                            });
                                })
                                .map(ResponseEntity::ok)
                                .onErrorResume(e -> {
                                    log.error("Failed to create call: {}", e.getMessage());
                                    return Mono.just(ResponseEntity.badRequest().build());
                                })
                );
    }

    @PostMapping("/status")
    public Mono<ResponseEntity<CallStatusResponse>> getCallStatus(@RequestBody CallStatusRequest statusRequest) {
        log.info("Received call status request: conversation_id={}, callSid={}",
                statusRequest.conversation_id(), statusRequest.callSid());

        // Try to find the call by callSid first, then by conversation_id in DB
        Mono<AgentCall> callMono = findCallByIdentifiers(statusRequest.callSid(), statusRequest.conversation_id());

        return callMono.flatMap(call -> {
            log.info("Found call in database: callId={}, status={}", call.callId(), call.status());

            // If rawResponse is present and recent, return its status
            String status = call.status();
            if (status != null && !status.isBlank()) {
                log.info("Returning cached status: {}", status);
                return Mono.just(ResponseEntity.ok(
                        new CallStatusResponse(call.callId(), status, call.rawResponse())));
            }

            // Otherwise query ElevenLabs for latest call details
            String callId = call.callId() != null ? call.callId() : statusRequest.callSid();
            if (callId == null) {
                log.warn("No call ID available to query ElevenLabs");
                return Mono.just(ResponseEntity.ok(
                        new CallStatusResponse(null, "unknown", call.rawResponse())));
            }

            log.info("Fetching latest status from ElevenLabs for callId: {}", callId);
            return elevenLabsClient.getCallTranscript(callId)
                    .flatMap(details -> {
                        String newStatus = elevenLabsClient.extractCallStatus(details);
                        log.info("Retrieved status from ElevenLabs: {}", newStatus);
                        AgentCall updated = call.withStatusUpdate(newStatus, details);
                        return agentCallRepository.save(updated)
                                .map(saved -> ResponseEntity.ok(
                                        new CallStatusResponse(saved.callId(), newStatus, saved.rawResponse())));
                    })
                    .onErrorResume(e -> {
                        log.error("Failed to fetch status from ElevenLabs: {}", e.getMessage());
                        return Mono.just(ResponseEntity.ok(
                                new CallStatusResponse(call.callId(), call.status(), call.rawResponse())));
                    });
        }).switchIfEmpty(Mono.defer(() -> {
            log.warn("Call not found in database for conversation_id={}, callSid={}",
                    statusRequest.conversation_id(), statusRequest.callSid());
            return Mono.just(ResponseEntity.notFound().build());
        }));
    }

    private Mono<AgentCall> findCallByIdentifiers(String callSid, String conversationId) {
        // Try callSid first
        if (callSid != null && !callSid.isBlank()) {
            return agentCallRepository.findByCallId(callSid)
                    .doOnNext(call -> log.debug("Found call by callSid: {}", callSid));
        }

        // Fallback to conversation_id
        if (conversationId != null && !conversationId.isBlank()) {
            return agentCallRepository.findAll()
                    .filter(ac -> ac.rawResponse() != null
                            && ac.rawResponse().has("conversation_id")
                            && conversationId.equals(ac.rawResponse().get("conversation_id").asText()))
                    .next()
                    .doOnNext(call -> log.debug("Found call by conversation_id: {}", conversationId));
        }

        return Mono.empty();
    }

}
