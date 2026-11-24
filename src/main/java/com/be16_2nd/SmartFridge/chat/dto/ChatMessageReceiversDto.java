package com.be16_2nd.SmartFridge.chat.dto;

import com.be16_2nd.SmartFridge.chat.domain.ChatMessage;

import java.util.List;
import java.util.UUID;

// 저장한 메시지와 알림 받을 회원 id 목록 (보낸 사람·지금 보고 있는 참여자 제외)
public record ChatMessageReceiversDto(ChatMessage chatMessage
        , UUID senderId
        , List<UUID> receiverIds) {

}
