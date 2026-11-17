package com.be16_2nd.SmartFridge.recipe.source;

import com.be16_2nd.SmartFridge.recipe.domain.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * COOKRCP01 응답의 레시피 한 건 (필드 이름은 공공데이터 원본 그대로)
 *
 * @param sourceId 원본 레시피 번호 (RCP_SEQ)
 * @param steps    MANUAL01~20 중 내용이 있는 것만, 원본 순서대로
 */
public record RecipeSourceRow(
        Long sourceId,
        String name,
        String dishType,
        String cookingMethod,
        String ingredientsText,
        String mainImageUrl,
        String thumbnailUrl,
        Integer calories,
        String tip,
        List<Recipe.StepContent> steps
) {
    private static final int MAX_STEPS = 20;
    // "1. 손질된 새우를 데친다." 앞의 번호
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d+\\s*[.)]\\s*");
    // 원본 일부 단계 끝에 붙어 있는 이미지 표시용 영문자 ("...건진다.a")
    private static final Pattern TRAILING_MARK = Pattern.compile("(?<=[.!?)\\s가-힣])[a-zA-Z]$");

    public static RecipeSourceRow from(Map<String, String> row) {
        List<Recipe.StepContent> steps = new ArrayList<>();
        for (int i = 1; i <= MAX_STEPS; i++) {
            String description = cleanStep(row.get(String.format("MANUAL%02d", i)));
            if (description != null) {
                steps.add(new Recipe.StepContent(description, blankToNull(row.get(String.format("MANUAL_IMG%02d", i)))));
            }
        }
        return new RecipeSourceRow(
                parseSourceId(row.get("RCP_SEQ")),
                requireName(row.get("RCP_NM")),
                blankToNull(row.get("RCP_PAT2")),
                blankToNull(row.get("RCP_WAY2")),
                blankToNull(row.get("RCP_PARTS_DTLS")),
                blankToNull(row.get("ATT_FILE_NO_MAIN")),
                blankToNull(row.get("ATT_FILE_NO_MK")),
                parseCalories(row.get("INFO_ENG")),
                blankToNull(row.get("RCP_NA_TIP")),
                steps);
    }

    static String cleanStep(String raw) {
        String text = blankToNull(raw);
        if (text == null) {
            return null;
        }
        text = text.replaceAll("\\s*\\R\\s*", " ");
        text = LEADING_NUMBER.matcher(text).replaceFirst("");
        text = TRAILING_MARK.matcher(text).replaceFirst("").strip();
        return text.isEmpty() ? null : text;
    }

    private static Long parseSourceId(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("레시피 번호(RCP_SEQ)가 올바르지 않습니다: " + value, e);
        }
    }

    private static String requireName(String value) {
        String name = blankToNull(value);
        if (name == null) {
            throw new IllegalArgumentException("레시피 이름(RCP_NM)이 비어 있습니다.");
        }
        return name;
    }

    private static Integer parseCalories(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
