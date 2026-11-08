package com.be16_2nd.SmartFridge.post.domain;

import com.be16_2nd.SmartFridge.post.dto.PostUpdateDto;
import com.be16_2nd.SmartFridge.common.domain.BaseTimeEntity;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class Post extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;
    @Column(nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostCategory category;

    // 상단 고정 여부와 고정한 시각 (공지사항만 고정 가능, 고정 글끼리는 최근에 고정한 글이 위)
    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;
    private LocalDateTime pinnedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id")
    private Fridge fridge;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostImage> images = new ArrayList<>();

    public void updatePost(PostUpdateDto postUpdateDto) {
        this.title = postUpdateDto.getTitle();
        this.content = postUpdateDto.getContent();
        this.category = postUpdateDto.getCategory();
        // 고정은 공지사항만 가능하므로, 다른 카테고리로 바꾸면 고정 해제
        if (this.pinned && this.category != PostCategory.공지사항) {
            this.pinned = false;
            this.pinnedAt = null;
        }
    }

    // 고정 / 해제 전환
    public void togglePin() {
        this.pinned = !this.pinned;
        this.pinnedAt = this.pinned ? LocalDateTime.now() : null;
    }

    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}
