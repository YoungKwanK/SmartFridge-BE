package com.be16_2nd.SmartFridge.recipe.controller;

import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.recipe.service.RecipeAiRecommendService;
import com.be16_2nd.SmartFridge.recipe.service.RecipeRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/fridge/{fridgeId}/recipes")
public class RecipeController {

    private final RecipeRecommendService recipeRecommendService;
    private final RecipeAiRecommendService recipeAiRecommendService;

    // 냉장고 재료가 많이 겹치는 레시피 (부족한 재료가 적은 순, 기본 양념은 세지 않음)
    @GetMapping("/recommend")
    public ResponseEntity<?> recommend(@PathVariable("fridgeId") Long fridgeId,
                                       @RequestParam(required = false) Integer size) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(recipeRecommendService.recommend(fridgeId, size))
                        .status_code(HttpStatus.OK.value())
                        .status_message("레시피 추천 완료")
                        .build(), HttpStatus.OK);
    }

    // 재료 겹침 상위 후보를 AI가 다시 골라 순서를 정하고 추천 이유를 붙인다 (AI 실패 시 aiRanked=false 로 겹침 순서 그대로)
    @GetMapping("/recommend/ai")
    public ResponseEntity<?> recommendWithAi(@PathVariable("fridgeId") Long fridgeId,
                                             @RequestParam(required = false) Integer size) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(recipeAiRecommendService.recommend(fridgeId, size))
                        .status_code(HttpStatus.OK.value())
                        .status_message("AI 레시피 추천 완료")
                        .build(), HttpStatus.OK);
    }

    // 레시피 상세 (재료마다 이 냉장고에 있는지 표시)
    @GetMapping("/{recipeId}")
    public ResponseEntity<?> detail(@PathVariable("fridgeId") Long fridgeId,
                                    @PathVariable("recipeId") Long recipeId) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(recipeRecommendService.detail(fridgeId, recipeId))
                        .status_code(HttpStatus.OK.value())
                        .status_message("레시피 상세 조회 완료")
                        .build(), HttpStatus.OK);
    }
}
