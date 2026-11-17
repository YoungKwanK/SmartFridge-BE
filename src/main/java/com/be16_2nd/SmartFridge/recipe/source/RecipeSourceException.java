package com.be16_2nd.SmartFridge.recipe.source;

/** 레시피 공공데이터 API 호출 실패 (CommonExceptionHandler 가 502 로 응답한다) */
public class RecipeSourceException extends RuntimeException {
    public RecipeSourceException(String message) {
        super(message);
    }

    public RecipeSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
