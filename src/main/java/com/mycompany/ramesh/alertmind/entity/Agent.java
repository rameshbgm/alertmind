package com.mycompany.ramesh.alertmind.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "agents")
public record Agent(
        @Id String id,
        @Indexed(unique = true) String agentId,
        String name,
        String description,
        String voiceId,
        String language,
        String firstMessage,
        String systemPrompt,
        JsonNode rawResponse,
        Instant createdAt
) {
    public static Agent from(String agentId, String name, String description, 
                            String voiceId, String language, String firstMessage,
                            String systemPrompt, JsonNode rawResponse) {
        return new Agent(null, agentId, name, description, voiceId, language, 
                        firstMessage, systemPrompt, rawResponse, Instant.now());
    }
}
