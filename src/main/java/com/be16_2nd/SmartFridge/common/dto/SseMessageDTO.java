package com.be16_2nd.SmartFridge.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SseMessageDTO {
    private String type;
    private String senderEmail;
    private String receiverEmail;
    private Long fridgeId;
    private String contents;
    // 채팅 알림일 때만 채운다. 프론트가 어느 방의 미읽음을 올릴지 구분하는 데 쓴다
    private Long roomId;
}
