package com.be16_2nd.SmartFridge.recipe.service;

/** 레시피 적재가 이미 돌고 있을 때 (CommonExceptionHandler 가 409 로 응답한다) */
public class RecipeImportInProgressException extends RuntimeException {
    public RecipeImportInProgressException() {
        super("레시피 적재가 이미 진행 중이에요. 끝난 뒤 다시 시도해 주세요.");
    }
}
