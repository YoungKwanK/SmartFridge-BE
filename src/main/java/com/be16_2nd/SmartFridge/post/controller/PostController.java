package com.be16_2nd.SmartFridge.post.controller;

import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.post.dto.PostCreateDto;
import com.be16_2nd.SmartFridge.post.dto.PostResDto;
import com.be16_2nd.SmartFridge.post.dto.PostSearchDto;
import com.be16_2nd.SmartFridge.post.dto.PostUpdateDto;
import com.be16_2nd.SmartFridge.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fridge/{fridgeId}/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/create")
    public ResponseEntity<?> create(@PathVariable("fridgeId") Long fridgeId, @RequestBody PostCreateDto createDto) {
        Long postId = postService.createPost(fridgeId, createDto);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(postId)
                        .status_code(HttpStatus.CREATED.value())
                        .status_message("게시글 등록이 완료되었습니다.")
                        .build(),
                HttpStatus.CREATED);
    }

    @PutMapping("/{postId}/update")
    public ResponseEntity<?> update(@PathVariable("fridgeId") Long fridgeId, @PathVariable("postId") Long postId, @RequestBody PostUpdateDto updateDto) {
        Long updatedPostId = postService.updatePost(fridgeId, postId, updateDto);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(updatedPostId)
                        .status_code(HttpStatus.OK.value())
                        .status_message("게시글 수정이 완료되었습니다.")
                        .build(),
                HttpStatus.OK);
    }

    @DeleteMapping("/delete/{postId}")
    public ResponseEntity<?> delete(@PathVariable("fridgeId") Long fridgeId, @PathVariable("postId") Long postId) {
        postService.deletePost(fridgeId, postId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .status_code(HttpStatus.OK.value())
                        .status_message("게시글 삭제가 완료되었습니다.")
                        .build(),
                HttpStatus.OK);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> postDetail(@PathVariable("fridgeId") Long fridgeId, @PathVariable("postId") Long postId) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(postService.findById(fridgeId, postId))
                        .status_code(HttpStatus.OK.value())
                        .status_message("게시글 상세 조회 완료")
                        .build(),
                HttpStatus.OK);
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> postLike(@PathVariable("fridgeId") Long fridgeId,@PathVariable("postId") Long postId){
        postService.addLike(fridgeId, postId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .status_code(HttpStatus.OK.value())
                        .status_message("좋아요 처리가 완료되었습니다.")
                        .build(),
                HttpStatus.OK);
    }

    @DeleteMapping("/{postId}/like")
    public ResponseEntity<?> removeLike(@PathVariable("fridgeId") Long fridgeId,@PathVariable("postId") Long postId){
        postService.removeLike(fridgeId, postId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .status_code(HttpStatus.OK.value())
                        .status_message("좋아요 취소가 완료되었습니다.")
                        .build(),
                HttpStatus.OK);
    }

    // 상단 고정 / 해제 전환 (냉장고 관리자만). result: 전환 후 고정 여부
    @PatchMapping("/{postId}/pin")
    public ResponseEntity<?> togglePin(@PathVariable("fridgeId") Long fridgeId, @PathVariable("postId") Long postId) {
        boolean pinned = postService.togglePin(fridgeId, postId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(pinned)
                        .status_code(HttpStatus.OK.value())
                        .status_message(pinned ? "게시글을 상단에 고정했습니다." : "게시글 고정을 해제했습니다.")
                        .build(),
                HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<?> postList(@PathVariable("fridgeId") Long fridgeId, Pageable pageable,
                                         @ModelAttribute PostSearchDto searchDto, @RequestParam(required = false, defaultValue = "전체") String category) {
        Page<PostResDto> postPage = postService.findByPosts(fridgeId, pageable, searchDto, category);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(postPage)
                        .status_code(HttpStatus.OK.value())
                        .status_message("게시글 목록 조회 완료")
                        .build(),
                HttpStatus.OK);
    }
}