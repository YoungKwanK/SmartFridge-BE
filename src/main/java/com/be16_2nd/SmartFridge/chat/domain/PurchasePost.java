package com.be16_2nd.SmartFridge.chat.domain;

import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.post.domain.Post;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/*
 * 공동구매 모집 정보 (채팅방과 1:1, 게시판 글과 1:1)
 * - 본문·댓글·좋아요·이미지는 게시판 글(post)에 있다
 * - 게시판 글이 삭제돼도 채팅방은 남으므로 post 는 NULL 이 될 수 있다 (title 은 채팅방 이름으로 따로 둔다)
 * - 냉장고는 chatRoom.fridge 로 접근
 * - 현재 참여자 수는 chatRoom.participants 로 계산
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
public class PurchasePost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false, unique = true)
    private ChatRoom chatRoom;

    // 게시판 글. 게시글을 지우면 DB 에서 NULL 로 바뀐다 (냉장고·회원 삭제처럼 cascade 순서가 섞여도 FK 오류가 안 나게)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", unique = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Member creator;

    // 채팅방 이름 (게시글 제목을 수정하면 같이 바뀐다)
    @Column(nullable = false)
    private String title;

    private String productUrl;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private JoinStatus joinStatus = JoinStatus.OPEN;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdTime;

    public int getCurrentParticipants() {
        return chatRoom.getParticipants().size();
    }

    public boolean isFull() {
        return getCurrentParticipants() >= maxParticipants;
    }

    public void updateJoinStatus(JoinStatus joinStatus) {
        this.joinStatus = joinStatus;
    }

    public void updateIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    // 게시판 글만 지우고 채팅방은 남길 때
    public void detachPost() {
        this.post = null;
    }
}
