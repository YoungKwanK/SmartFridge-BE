package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;

/*
 * 채팅방 참여자에서 빠진 회원 (방 나가기, 냉장고 나가기·강퇴, 관리자 위임으로 교체된 이전 관리자)
 * 트랜잭션이 커밋되면 ChatSubscriptionRevoker 가 이 회원의 구독을 끊는다
 */
public record ChatSubscriptionRevoked(ChatRoomType type, Long roomId, String email) {
}
