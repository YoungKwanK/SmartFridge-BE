package com.be16_2nd.SmartFridge.recipe.ingredient;

import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientParser.ParsedIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재료 문자열 파서 단위 테스트. 입력은 식품안전나라 COOKRCP01 실제 응답에서 가져왔다.
 */
class IngredientParserTest {

    @Test
    void 요리이름_줄과_고명_제목은_건너뛰고_재료와_분량을_나눈다() {
        String text = """
                새우두부계란찜
                연두부 75g(3/4모), 칵테일새우 20g(5마리), 달걀 30g(1/2개), 생크림 13g(1큰술), 설탕 5g(1작은술), 무염버터 5g(1작은술)
                고명
                시금치 10g(3줄기)""";

        List<ParsedIngredient> result = IngredientParser.parse(text);

        assertThat(result).extracting(ParsedIngredient::name)
                .containsExactly("연두부", "칵테일새우", "달걀", "생크림", "설탕", "무염버터", "시금치");
        assertThat(result.get(0).amount()).isEqualTo("75g(3/4모)");
        assertThat(result.get(6).amount()).isEqualTo("10g(3줄기)");
    }

    @Test
    void 인분_표시와_양념장_제목을_떼고_손질_방법을_뺀다() {
        String text = """
                [1인분]조선부추 50g, 날콩가루 7g(1⅓작은술)
                ·양념장 : 저염간장 3g(2/3작은술), 다진 대파 5g(1작은술), 다진 마늘 2g(1/2쪽), 참깨 약간""";

        List<ParsedIngredient> result = IngredientParser.parse(text);

        assertThat(result).extracting(ParsedIngredient::name)
                .containsExactly("조선부추", "날콩가루", "저염간장", "대파", "마늘", "참깨");
        assertThat(result).extracting(ParsedIngredient::amount)
                .containsExactly("50g", "7g(1⅓작은술)", "3g(2/3작은술)", "5g(1작은술)", "2g(1/2쪽)", "약간");
    }

    @Test
    void 콜론만_있는_제목_줄과_줄_끝_쉼표를_처리한다() {
        String text = """
                ●방울토마토 소박이 :\s
                방울토마토 150g(5개), 양파 10g(3×1cm), 부추 10g(5줄기)
                ●양념장 :\s
                고춧가루 4g(1작은술), 물 2ml(1/3작은술), 통깨 약간
                북엇국
                북어채 25g(15개), 양파 40g(1/4개),
                표고버섯 20g(2장), 물 300ml(1½컵)""";

        List<ParsedIngredient> result = IngredientParser.parse(text);

        assertThat(result).extracting(ParsedIngredient::name)
                .containsExactly("방울토마토", "양파", "부추", "고춧가루", "물", "통깨",
                        "북어채", "양파", "표고버섯", "물");
    }

    @Test
    void 이름의_괄호_설명은_빼고_괄호_안_쉼표로_나누지_않는다() {
        List<ParsedIngredient> result = IngredientParser.parse("두부(부침용, 단단한 것) 100g, 돼지고기(앞다리살 100g)");

        assertThat(result).extracting(ParsedIngredient::name).containsExactly("두부", "돼지고기");
        assertThat(result.get(0).amount()).isEqualTo("100g");
    }

    @Test
    void 괄호_짝이_안_맞는_원본은_쉼표로만_나눈다() {
        // 실제 데이터 781번 "갯벌의 여왕 모시조개의 달콤한 유혹"
        List<ParsedIngredient> result = IngredientParser.parse(
                "바지락(모시조개( 200g(30개), 양파 15g(2*4cm), 마늘 10g(2쪽), 후추 약간");

        assertThat(result).extracting(ParsedIngredient::name).containsExactly("바지락", "양파", "마늘", "후추");
        assertThat(result.get(0).amount()).isEqualTo("200g(30개)");
    }

    @Test
    void 분량이_괄호_안에만_있으면_괄호를_벗긴다() {
        // 실제 데이터 1121번 "감자주스"
        List<ParsedIngredient> result = IngredientParser.parse("감자(400g), 우유(200g), 설탕(10g), 다진 마늘(10g), 삼색분말(각 10g)");

        assertThat(result).containsExactly(
                new ParsedIngredient("감자", "400g"),
                new ParsedIngredient("우유", "200g"),
                new ParsedIngredient("설탕", "10g"),
                new ParsedIngredient("마늘", "10g"),
                new ParsedIngredient("삼색분말", "각 10g"));
    }

    @Test
    void 분량_뒤에_붙은_괄호와_이름의_괄호_설명은_그대로_다룬다() {
        List<ParsedIngredient> result = IngredientParser.parse("연두부 75g(3/4모), 두부(부침용) 100g, 돼지고기(앞다리살 100g)");

        assertThat(result).containsExactly(
                new ParsedIngredient("연두부", "75g(3/4모)"),
                new ParsedIngredient("두부", "100g"),
                new ParsedIngredient("돼지고기", "앞다리살 100g"));
    }

    @Test
    void 줄_중간의_소스소개_라벨은_앞_재료를_버리지_않는다() {
        // 실제 데이터 724번 "쇠고기 채소볶음" (원본은 한 줄)
        List<ParsedIngredient> result = IngredientParser.parse(
                "쇠고기 40g, 생표고버섯 16g, 정종 2g, 식용유 9g [소스소개]쇠고기밑간양념:전분 2g, 달걀 2g");

        assertThat(result).extracting(ParsedIngredient::name)
                .containsExactly("쇠고기", "생표고버섯", "정종", "식용유", "전분", "달걀");
        assertThat(result).extracting(ParsedIngredient::amount)
                .containsExactly("40g", "16g", "2g", "9g", "2g", "2g");
    }

    @Test
    void 닫히지_않은_태그와_분량_뒤_라벨과_br_줄바꿈을_처리한다() {
        // 실제 데이터 1022번, 986번, 706번
        List<ParsedIngredient> result = IngredientParser.parse(
                "감자 40g, 오이 10g [소스소개 단촛물 : 물60g, 식초 1.5g\n"
                        + "달걀 12g, 토마토 120g  토마토소스 : 셀러리 8g\n"
                        + "배 12g [소스소개] 고기재움장 : 포도주 2.5g<br />후춧가루 0.1g");

        assertThat(result).extracting(ParsedIngredient::name)
                .containsExactly("감자", "오이", "물", "식초", "달걀", "토마토", "셀러리", "배", "포도주", "후춧가루");
    }

    @Test
    void 괄호_안의_콜론은_라벨로_보지_않는다() {
        List<ParsedIngredient> result = IngredientParser.parse("소스 : 간장(1:2 희석) 10g, 식초 5g");

        assertThat(result).containsExactly(
                new ParsedIngredient("간장", "10g"),
                new ParsedIngredient("식초", "5g"));
    }

    @Test
    void 분량이_너무_길면_자르고_이름이_너무_길면_버린다() {
        ParsedIngredient longAmount = IngredientParser.parseItem("소금 1" + "가".repeat(200));
        ParsedIngredient longName = IngredientParser.parseItem("가".repeat(40) + " 10g");

        assertThat(longAmount.amount()).hasSize(IngredientParser.MAX_AMOUNT_LENGTH);
        assertThat(longName).isNull();
    }

    @Test
    void 분량이_없으면_분량은_null() {
        List<ParsedIngredient> result = IngredientParser.parse("소금, 후추");

        assertThat(result).containsExactly(new ParsedIngredient("소금", null), new ParsedIngredient("후추", null));
    }

    @Test
    void 비어있으면_빈_목록() {
        assertThat(IngredientParser.parse(null)).isEmpty();
        assertThat(IngredientParser.parse("  \n ")).isEmpty();
    }
}
