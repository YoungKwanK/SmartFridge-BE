package com.be16_2nd.SmartFridge.agent.service;

import com.be16_2nd.SmartFridge.agent.repository.AgentActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 확인이 실패했을 때 제안 상태(EXPIRED, FAILED)를 남긴다.
 * 확인 트랜잭션은 예외로 되돌려지므로, 상태는 따로 새 트랜잭션에서 저장해야 남는다.
 * 그 사이 다른 요청이 먼저 처리했다면 (PENDING이 아니면) 건드리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AgentActionStatusRecorder {

    private final AgentActionRepository agentActionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExpired(UUID actionId) {
        agentActionRepository.findById(actionId)
                .filter(action -> action.isPending())
                .ifPresent(action -> action.expire(LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID actionId, String reason) {
        agentActionRepository.findById(actionId)
                .filter(action -> action.isPending())
                .ifPresent(action -> action.fail(reason, LocalDateTime.now()));
    }
}
