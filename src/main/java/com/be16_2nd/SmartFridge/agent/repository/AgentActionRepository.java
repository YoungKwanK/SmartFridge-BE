package com.be16_2nd.SmartFridge.agent.repository;

import com.be16_2nd.SmartFridge.agent.domain.AgentAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {
}
