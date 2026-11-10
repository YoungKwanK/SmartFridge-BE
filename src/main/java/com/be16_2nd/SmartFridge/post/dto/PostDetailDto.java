package com.be16_2nd.SmartFridge.post.dto;

import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PostDetailDto {
    private Long id;
    private String title;
    private String content;
    private String writer;
    private String categoryName;
    private boolean pinned;
    private LocalDateTime createdDate;
    private List<String> imageUrls;
    private int commentCount;
    private Long viewCount;
    private Long likeCount;
    private Boolean isLiked;
    private PostPurchaseDto purchase;

    public static PostDetailDto fromEntity(Post post, Long viewCount, Long likeCount, Boolean isLiked ) {
        return PostDetailDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .categoryName(post.getCategory().name())
                .pinned(post.isPinned())
                .writer(post.getMember().getName())
                .createdDate(post.getCreatedTime())
                .imageUrls(post.getImages().stream()
                        .map(PostImage::getImageUrl)
                        .collect(Collectors.toList()))
                .commentCount(post.getCommentCount())
                .viewCount(viewCount)
                .likeCount(likeCount)
                .isLiked(isLiked)
                .build();
    }
}
