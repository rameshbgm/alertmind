package com.mycompany.ramesh.alertmind.repository;

import com.mycompany.ramesh.alertmind.entity.AgentCall;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface AgentCallRepository extends ReactiveMongoRepository<AgentCall, String> {
	Mono<AgentCall> findByCallId(String callId);
}
