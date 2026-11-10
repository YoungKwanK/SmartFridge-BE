package com.be16_2nd.SmartFridge.post.dto;

import com.be16_2nd.SmartFridge.chat.domain.PurchasePost;
import com.be16_2nd.SmartFridge.member.domain.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 게시글 상세의 공동구매 모집 정보 (공동구매 글이 아니거나 채팅방이 없으면 null)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PostPurchaseDto {
    private Long roomId;
    private Integer currentParticipants;
    private Integer maxParticipants;
    private String joinStatus;
    private Boolean isActive;
    private String productUrl;
    private Boolean isJoined;
    private Boolean isCreator;

    public static PostPurchaseDto fromEntity(PurchasePost purchasePost, Member viewer) {
        return PostPurchaseDto.builder()
                .roomId(purchasePost.getChatRoom().getId())
                .currentParticipants(purchasePost.getCurrentParticipants())
                .maxParticipants(purchasePost.getMaxParticipants())
                .joinStatus(purchasePost.getJoinStatus().toString())
                .isActive(purchasePost.getIsActive())
                .productUrl(purchasePost.getProductUrl())
                .isJoined(purchasePost.getChatRoom().getParticipants().stream()
                        .anyMatch(p -> p.getMember().getId().equals(viewer.getId())))
                .isCreator(purchasePost.getCreator().getId().equals(viewer.getId()))
                .build();
    }
}
