package com.be16_2nd.SmartFridge.chat.dto;

import java.util.UUID;

import com.be16_2nd.SmartFridge.chat.domain.ChatMessage;
import com.be16_2nd.SmartFridge.chat.domain.ChatMessageImage;
import com.be16_2nd.SmartFridge.member.domain.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ChatMessageDto {
    private Long roomId;
    private String chatRoomType;
    private String messageType;
    private String message;
    private UUID senderId;
    private String senderName;
    private List<String> imageUrls;
    // 관리자 1대1 내역 조회에서만 채운다 (상대 회원 id)
    private UUID receiverId;

    private String timestamp;

    public static ChatMessageDto fromEntity(ChatMessage chatMessage){
        Member sender = chatMessage.getSender();
        return ChatMessageDto.builder()
                .roomId(chatMessage.getChatRoom().getId())
                .chatRoomType(chatMessage.getChatRoom().getType().toString())
                .messageType(chatMessage.getMessageType().toString())
                .timestamp(chatMessage.getCreatedTime().toString())
                .imageUrls(chatMessage.getChatMessageImages().stream().map(ChatMessageImage::getImageUrl).toList())
                .message(chatMessage.getContents())
                .senderId(sender != null ? sender.getId() : null)
                .senderName(sender != null ? sender.getName() : null)
                .build();
    }
}
