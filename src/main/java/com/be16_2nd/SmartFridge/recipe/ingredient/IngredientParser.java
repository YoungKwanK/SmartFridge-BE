package com.be16_2nd.SmartFridge.recipe.ingredient;

import java.util.ArrayList;
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
 */
public final class IngredientParser {

    // 분량이 시작되는 지점: 숫자, 분수 기호, 정해진 분량 표현
    private static final Pattern AMOUNT_START =
            Pattern.compile("\\d|[½⅓⅔¼¾⅛]|약간|적당량|적당히|조금|소량|한\\s?줌|한\\s?꼬집");
    private static final Pattern LEADING_BRACKETS = Pattern.compile("^(\\s*\\[[^\\]]*\\])+");
    private static final Pattern LEADING_BULLETS = Pattern.compile("^[\\s·●•○◎▶■□◆◇※*\\-]+");
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)?");

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
        for (String rawLine : text.split("\\R")) {
            String line = stripLineHead(rawLine);
            int colon = indexOfColon(line);
            if (colon >= 0) {
                line = line.substring(colon + 1).strip();   // "양념장 : 간장 1큰술" → "간장 1큰술"
            }
            if (line.isEmpty() || isTitle(line)) {
                continue;
            }
            for (String item : splitOutsideParentheses(line)) {
                ParsedIngredient parsed = parseItem(item);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        return result;
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
            namePart = text.substring(0, m.start());
            amount = text.substring(m.start()).strip();
        }
        // "두부(부침용) 100g" → "두부", "돼지고기(앞다리살 100g)" → "돼지고기"
        String name = PARENTHESES.matcher(namePart).replaceAll(" ");
        name = stripModifiers(name.replaceAll("\\s+", " ").strip());
        if (name.isEmpty()) {
            return null;
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

    private static int indexOfColon(String line) {
        int half = line.indexOf(':');
        int full = line.indexOf('：');
        if (half < 0) return full;
        if (full < 0) return half;
        return Math.min(half, full);
    }

    // 쉼표도 분량도 없는 줄 = "고명", "북엇국" 같은 제목
    private static boolean isTitle(String line) {
        return !line.contains(",") && !AMOUNT_START.matcher(line).find();
    }

    private static List<String> splitOutsideParentheses(String line) {
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
