package com.mycompany.ramesh.alertmind.repository;

import com.mycompany.ramesh.alertmind.entity.Agent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface AgentRepository extends ReactiveMongoRepository<Agent, String> {
    Mono<Agent> findByAgentId(String agentId);
    Mono<Void> deleteByAgentId(String agentId);
}
