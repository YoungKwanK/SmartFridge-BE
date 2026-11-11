package com.be16_2nd.SmartFridge.chat.domain;

import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(indexes = @Index(name = "idx_chat_message_room_id", columnList = "chat_room_id, id"))
public class ChatMessage {
    // 메시지 글자 수 상한
    public static final int MAX_CONTENTS_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 시스템 메시지는 발신자가 없음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Member sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String contents;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdTime;

    // 채팅 내역 한 페이지의 이미지를 메시지마다가 아니라 한 번에 조회
    @Builder.Default
    @BatchSize(size = 100)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "chatMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessageImage> chatMessageImages = new ArrayList<>();

    public void addImage(String imageUrl) {
        this.chatMessageImages.add(ChatMessageImage.builder()
                .chatMessage(this)
                .imageUrl(imageUrl)
                .sortOrder(this.chatMessageImages.size())
                .build());
    }
}
