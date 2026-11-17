package com.be16_2nd.SmartFridge.agent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 에이전트가 만든 "실행 전 제안". 에이전트는 식품을 직접 바꾸지 못하고 여기에 제안만 남긴다.
 * 사용자가 화면에서 확인 버튼을 눌러야 서버가 다시 검사한 뒤 실행한다.
 *
 * 냉장고와 회원은 FoodHistory처럼 id 값만 저장한다 (탈퇴·냉장고 삭제에 막히지 않게).
 */
@Entity
@Table(name = "agent_action",
        indexes = @Index(name = "idx_agent_action_member_created", columnList = "member_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAction {

    public static final Duration VALID_DURATION = Duration.ofMinutes(10);

    // 확인 API 주소에 들어가므로 순번 대신 추측하기 어려운 UUID를 쓴다
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // 두 번 눌러도 한 번만 실행되게 한다. null이면 새 엔티티로 보고 바로 저장한다 (id를 직접 넣으므로)
    @Version
    private Long version;

    @Column(name = "fridge_id", nullable = false, updatable = false)
    private Long fridgeId;

    // 제안을 받은 회원. 이 회원만 확인·취소할 수 있다
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AgentActionType type;

    @Convert(converter = FoodIdListConverter.class)
    @Column(name = "food_ids", nullable = false, length = 1000, updatable = false)
    private List<Long> foodIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentActionStatus status;

    // 실패했을 때 사용자에게 보여 준 이유 (확인 시 다시 검사하다 걸린 내용)
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private AgentAction(Long fridgeId, UUID memberId, AgentActionType type, List<Long> foodIds, LocalDateTime now) {
        this.id = UUID.randomUUID();
        this.fridgeId = fridgeId;
        this.memberId = memberId;
        this.type = type;
        this.foodIds = List.copyOf(foodIds);
        this.status = AgentActionStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = now.plus(VALID_DURATION);
    }

    public static AgentAction propose(Long fridgeId, UUID memberId, AgentActionType type, List<Long> foodIds, LocalDateTime now) {
        if (foodIds == null || foodIds.isEmpty()) {
            throw new IllegalArgumentException("제안할 식품이 없어요.");
        }
        if (type == AgentActionType.DELETE && foodIds.size() != 1) {
            throw new IllegalArgumentException("잘못 등록한 식품 삭제는 한 번에 1개만 제안할 수 있어요.");
        }
        return new AgentAction(fridgeId, memberId, type, foodIds, now);
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isPending() {
        return status == AgentActionStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void confirm(LocalDateTime now) {
        changeFromPending(AgentActionStatus.CONFIRMED, now);
    }

    public void cancel(LocalDateTime now) {
        changeFromPending(AgentActionStatus.CANCELLED, now);
    }

    public void expire(LocalDateTime now) {
        changeFromPending(AgentActionStatus.EXPIRED, now);
    }

    public void fail(String reason, LocalDateTime now) {
        changeFromPending(AgentActionStatus.FAILED, now);
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
    }

    // 한 번 처리된 제안은 다시 바뀌지 않는다
    private void changeFromPending(AgentActionStatus next, LocalDateTime now) {
        if (!isPending()) {
            throw new IllegalStateException("이미 처리된 제안입니다: " + status);
        }
        this.status = next;
        this.processedAt = now;
    }
}
