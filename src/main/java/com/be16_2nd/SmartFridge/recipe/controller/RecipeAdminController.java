package com.be16_2nd.SmartFridge.recipe.controller;

import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.recipe.service.RecipeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/recipes")
public class RecipeAdminController {

    private final RecipeImportService recipeImportService;

    // 식품안전나라 레시피를 받아 DB에 넣는다 (원본 번호로 덮어쓰므로 여러 번 실행해도 된다)
    @PostMapping("/import")
    public ResponseEntity<?> importRecipes() {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(recipeImportService.importAll())
                        .status_code(HttpStatus.OK.value())
                        .status_message("레시피 적재 완료")
                        .build(), HttpStatus.OK);
    }
}
