package com.be16_2nd.SmartFridge.post.dto;

import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PostCreateDto {
    private String title;
    private String content;
    private PostCategory category;
    private List<String> imageUrls;

    // 공동구매 글일 때만 사용 (작성하면 채팅방이 같이 생긴다)
    private Integer maxParticipants;
    private String productUrl;

}
