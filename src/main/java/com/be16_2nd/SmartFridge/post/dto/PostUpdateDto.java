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
public class PostUpdateDto {
    private String title;
    private String content;
    private PostCategory category;
    private List<String> imageUrls;
}
