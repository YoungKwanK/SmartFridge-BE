package com.be16_2nd.SmartFridge.post.controller;

import com.be16_2nd.SmartFridge.post.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class PostCategoryController {
    @Autowired
    private PostService postService;

    // 게시판 카테고리 이름 목록 (예: ["공지사항", "자유게시판", "공동구매"])
    @GetMapping
    public ResponseEntity<?> getAllCategories() {
        List<String> categories = postService.findAllCategories();

        return ResponseEntity.ok(categories);
    }

}
