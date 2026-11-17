package com.be16_2nd.SmartFridge.recipe.ingredient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공공데이터 레시피의 재료 문자열(RCP_PARTS_DTLS)을 재료 하나하나로 나눈다.
 *
 * <pre>
 * [1인분]조선부추 50g, 날콩가루 7g(1⅓작은술)
 * ·양념장 : 저염간장 3g(2/3작은술), 다진 대파 5g(1작은술), 참깨 약간
 *   → 조선부추 / 50g, 날콩가루 / 7g(1⅓작은술), 저염간장 / 3g(2/3작은술), 대파 / 5g(1작은술), 참깨 / 약간
 * </pre>
 *
 * - "고명", "●양념장 :", 요리 이름처럼 분량도 쉼표도 없는 줄은 제목으로 보고 건너뛴다
 * - "다진 ", "채 썬 " 같은 손질 방법은 떼어낸다 (별칭은 {@link IngredientDictionary} 가 맡는다)
 * - "양념장 :" 같은 라벨은 재료 단위로 뗀다. 줄 중간의 "[소스소개]" 태그는 구분자, "&lt;br /&gt;"는 줄바꿈으로 본다
 * - "감자(400g)"처럼 분량이 괄호 안에만 있으면 괄호를 벗겨 분량으로 쓴다
 */
public final class IngredientParser {

    // 분량이 시작되는 지점: 숫자, 분수 기호, 정해진 분량 표현
    private static final Pattern AMOUNT_START =
            Pattern.compile("\\d|[½⅓⅔¼¾⅛]|약간|적당량|적당히|조금|소량|한\\s?줌|한\\s?꼬집");
    private static final Pattern LEADING_BRACKETS = Pattern.compile("^(\\s*\\[[^\\]]*\\])+");
    private static final Pattern LEADING_BULLETS = Pattern.compile("^[\\s·●•○◎▶■□◆◇※*\\-]+");
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)?");
    // 줄 중간의 "[소스소개]", 닫히지 않은 "[소스소개 단촛물 :" 의 여는 괄호
    private static final Pattern MID_BRACKET = Pattern.compile("\\[[^\\]]*\\]|\\[");
    private static final Pattern HTML_LINE_BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);

    static final int MAX_NAME_LENGTH = 30;
    static final int MAX_AMOUNT_LENGTH = 100;

    // 이름 앞에 붙는 손질 방법 (뒤에 띄어쓰기가 있을 때만 뗀다: "다진 마늘" → "마늘", "다진마늘"은 사전 별칭으로)
    private static final List<String> MODIFIERS = List.of(
            "곱게 다진", "굵게 다진", "다진", "채 썬", "채썬", "얇게 썬", "썬", "삶은", "데친", "불린", "손질한", "손질된",
            "볶은", "구운", "말린", "냉동", "깐", "곱게 간", "간", "찐", "으깬", "잘게 찢은", "찢은");

    private IngredientParser() {
    }

    public static List<ParsedIngredient> parse(String text) {
        List<ParsedIngredient> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String rawLine : HTML_LINE_BREAK.matcher(text).replaceAll("\n").split("\\R")) {
            // 줄 중간의 "[소스소개]" 같은 태그는 앞뒤 재료를 나누는 구분자로 본다
            String line = MID_BRACKET.matcher(stripLineHead(rawLine)).replaceAll(",").strip();
            if (line.isEmpty() || isTitle(line)) {
                continue;
            }
            for (String item : splitOutsideParentheses(line)) {
                for (String part : stripLabels(item)) {
                    ParsedIngredient parsed = parseItem(part);
                    if (parsed != null) {
                        result.add(parsed);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 재료 하나에 붙은 "라벨 :"을 뗀다. 콜론을 줄 단위로 처리하면 줄 중간에 라벨이 올 때 앞의 재료까지 버려지므로 재료 단위로 본다.
     * <pre>
     * "양념장 : 간장 1큰술"           → "간장 1큰술"
     * "토마토 120g  토마토소스 : 셀러리 8g" → "토마토 120g", "셀러리 8g"  (라벨 앞에 분량까지 있는 재료는 남긴다)
     * </pre>
     */
    static List<String> stripLabels(String item) {
        List<String> parts = new ArrayList<>();
        String rest = item;
        int colon;
        while ((colon = indexOfColonOutsideParentheses(rest)) >= 0) {
            String kept = ingredientBeforeLabel(rest.substring(0, colon));
            if (!kept.isEmpty()) {
                parts.add(kept);
            }
            rest = rest.substring(colon + 1);
        }
        parts.add(rest);
        return parts;
    }

    // 라벨 앞부분에서 마지막 분량까지를 재료로 남기고, 분량이 없으면 전부 라벨로 본다
    private static String ingredientBeforeLabel(String beforeColon) {
        String[] tokens = beforeColon.strip().split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (AMOUNT_START.matcher(tokens[i]).find()) {
                return String.join(" ", Arrays.copyOfRange(tokens, 0, i + 1));
            }
        }
        return "";
    }

    static ParsedIngredient parseItem(String item) {
        String text = item.strip();
        if (text.isEmpty()) {
            return null;
        }
        String namePart = text;
        String amount = null;
        Matcher m = AMOUNT_START.matcher(text);
        if (m.find()) {
            int start = m.start();
            int open = text.lastIndexOf('(', start);
            int close = open >= 0 && isUnclosed(text.substring(0, start)) ? closingIndex(text, open) : -1;
            if (close >= 0 && close < text.length() - 1 && m.find(close + 1)) {
                // "간장(1:2 희석) 10g": 괄호 안 숫자는 설명이고 괄호 뒤에 진짜 분량이 있다
                start = m.start();
            }
            if (close == text.length() - 1) {
                // "감자(400g)": 분량이 이름 뒤 괄호 하나에만 있으면 괄호를 벗겨 분량으로 쓴다
                namePart = text.substring(0, open);
                amount = text.substring(open + 1, close).strip();
            } else {
                namePart = text.substring(0, start);
                amount = text.substring(start).strip();
            }
        }
        // "두부(부침용) 100g" → "두부", "돼지고기(앞다리살 100g)" → "돼지고기"
        String name = PARENTHESES.matcher(namePart).replaceAll(" ");
        name = stripModifiers(name.replaceAll("\\s+", " ").strip());
        // 원본 오타로 여러 재료가 한 덩어리가 된 경우: 이름이 이렇게 길면 재료가 아니므로 버린다
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return null;
        }
        if (amount != null && amount.length() > MAX_AMOUNT_LENGTH) {
            amount = amount.substring(0, MAX_AMOUNT_LENGTH).strip();   // 화면 표시용이라 잘라도 된다 (DB 컬럼 100자)
        }
        return new ParsedIngredient(name, amount == null || amount.isEmpty() ? null : amount);
    }

    private static String stripModifiers(String name) {
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String modifier : MODIFIERS) {
                if (name.startsWith(modifier + " ") && name.length() > modifier.length() + 1) {
                    name = name.substring(modifier.length() + 1).strip();
                    stripped = true;
                    break;
                }
            }
        }
        return name;
    }

    private static String stripLineHead(String line) {
        String result = LEADING_BRACKETS.matcher(line.strip()).replaceFirst("");
        return LEADING_BULLETS.matcher(result).replaceFirst("").strip();
    }

    // 괄호 안의 콜론("비율(1:2)")은 라벨이 아니다
    private static int indexOfColonOutsideParentheses(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && depth > 0) depth--;
            if ((c == ':' || c == '：') && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // 여는 괄호가 닫는 괄호보다 많다 = 괄호 안에서 끝났다
    private static boolean isUnclosed(String text) {
        return text.chars().filter(c -> c == '(').count() > text.chars().filter(c -> c == ')').count();
    }

    // open 위치의 여는 괄호와 짝이 맞는 닫는 괄호 위치, 닫히지 않으면 -1
    private static int closingIndex(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // 쉼표도 분량도 없는 줄 = "고명", "북엇국" 같은 제목
    private static boolean isTitle(String line) {
        return !line.contains(",") && !AMOUNT_START.matcher(line).find();
    }

    private static List<String> splitOutsideParentheses(String line) {
        // "바지락(모시조개( 200g(30개), 양파 15g" 처럼 괄호 짝이 안 맞는 원본은 괄호를 믿지 않고 쉼표로만 나눈다
        if (line.chars().filter(c -> c == '(').count() != line.chars().filter(c -> c == ')').count()) {
            return List.of(line.split(","));
        }
        List<String> items = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')' && depth > 0) depth--;
            if (c == ',' && depth == 0) {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        items.add(current.toString());
        return items;
    }

    /**
     * @param name   손질 방법을 뗀 재료 이름 (사전 정규화 전)
     * @param amount 분량 원문, 없으면 null
     */
    public record ParsedIngredient(String name, String amount) {
    }
}
