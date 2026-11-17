package com.be16_2nd.SmartFridge.recipe.source;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 응답 해석만 확인한다 (실제 호출은 하지 않는다). 응답 형식은 식품안전나라 실제 응답을 줄인 것.
 */
class RecipeSourceClientTest {

    final RecipeSourceClient client = new RecipeSourceClient(
            new RecipeSourceProperties("http://localhost", "test-key", 100, Duration.ofSeconds(5)));

    @Test
    void 성공_응답이면_전체_건수와_행을_돌려준다() {
        String body = """
                {"COOKRCP01":{"total_count":"1156","row":[
                  {"RCP_SEQ":"28","RCP_NM":"새우 두부 계란찜","MANUAL01":"1. 데친다."},
                  {"RCP_SEQ":"29","RCP_NM":"부추 콩가루 찜"}
                ],"RESULT":{"MSG":"정상처리되었습니다.","CODE":"INFO-000"}}}""";

        RecipeSourceClient.RecipeSourcePage page = client.parse(body, 1, 2);

        assertThat(page.totalCount()).isEqualTo(1156);
        assertThat(page.rows()).hasSize(2);
        assertThat(page.rows().get(0)).containsEntry("RCP_NM", "새우 두부 계란찜").containsEntry("MANUAL01", "1. 데친다.");
    }

    @Test
    void 데이터가_없으면_빈_페이지() {
        String body = """
                {"COOKRCP01":{"total_count":"0","RESULT":{"MSG":"해당하는 데이터가 없습니다.","CODE":"INFO-200"}}}""";

        assertThat(client.parse(body, 2001, 2100).rows()).isEmpty();
    }

    @Test
    void 인증키가_틀리면_오류_코드와_메시지로_실패한다() {
        String body = """
                {"COOKRCP01":{"total_count":"","RESULT":{"MSG":"인증키가 유효하지 않습니다.","CODE":"INFO-100"}}}""";

        assertThatThrownBy(() -> client.parse(body, 1, 100))
                .isInstanceOf(RecipeSourceException.class)
                .hasMessageContaining("INFO-100")
                .hasMessageContaining("인증키가 유효하지 않습니다.");
    }

    @Test
    void JSON이_아니거나_형식이_다르면_실패한다() {
        assertThatThrownBy(() -> client.parse("<html>점검 중</html>", 1, 100))
                .isInstanceOf(RecipeSourceException.class);
        assertThatThrownBy(() -> client.parse("{\"OTHER\":{}}", 1, 100))
                .isInstanceOf(RecipeSourceException.class);
    }

    @Test
    void 키가_없거나_sample이면_시험용_키로_본다() {
        assertThat(new RecipeSourceProperties("u", "", 100, null).isSampleKey()).isTrue();
        assertThat(new RecipeSourceProperties("u", null, 100, null).effectiveApiKey()).isEqualTo("sample");
        assertThat(new RecipeSourceProperties("u", "real", 100, null).isSampleKey()).isFalse();
    }
}
