package com.be16_2nd.SmartFridge.recipe.source;

import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeSourceRowTest {

    @Test
    void 원본_필드를_옮기고_빈_단계는_건너뛴다() {
        Map<String, String> raw = row();
        raw.put("MANUAL01", "1. 손질된 새우를 끓는 물에 데쳐 건진다.a");
        raw.put("MANUAL_IMG01", "http://img/1.png");
        raw.put("MANUAL02", "");
        raw.put("MANUAL03", "3. 찜기에 넣고 10분 정도 찐다.c");

        RecipeSourceRow result = RecipeSourceRow.from(raw);

        assertThat(result.sourceId()).isEqualTo(28L);
        assertThat(result.name()).isEqualTo("새우 두부 계란찜");
        assertThat(result.dishType()).isEqualTo("반찬");
        assertThat(result.calories()).isEqualTo(220);
        assertThat(result.tip()).isNull();
        assertThat(result.steps()).containsExactly(
                new Recipe.StepContent("손질된 새우를 끓는 물에 데쳐 건진다.", "http://img/1.png"),
                new Recipe.StepContent("찜기에 넣고 10분 정도 찐다.", null));
    }

    @Test
    void 단계_정리는_단위_글자는_건드리지_않는다() {
        assertThat(RecipeSourceRow.cleanStep("2. 5cm 길이로 썬다 200g")).isEqualTo("5cm 길이로 썬다 200g");
        assertThat(RecipeSourceRow.cleanStep("물기를 뺀다.\n그릇에 담는다.b")).isEqualTo("물기를 뺀다. 그릇에 담는다.");
        assertThat(RecipeSourceRow.cleanStep("  ")).isNull();
    }

    @Test
    void 열량이_숫자가_아니면_null() {
        Map<String, String> raw = row();
        raw.put("INFO_ENG", "모름");

        assertThat(RecipeSourceRow.from(raw).calories()).isNull();
    }

    @Test
    void 번호나_이름이_없으면_실패한다() {
        Map<String, String> noName = row();
        noName.put("RCP_NM", " ");
        Map<String, String> badSeq = row();
        badSeq.put("RCP_SEQ", "abc");

        assertThatThrownBy(() -> RecipeSourceRow.from(noName)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeSourceRow.from(badSeq)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, String> row() {
        Map<String, String> raw = new HashMap<>();
        raw.put("RCP_SEQ", "28");
        raw.put("RCP_NM", "새우 두부 계란찜");
        raw.put("RCP_PAT2", "반찬");
        raw.put("RCP_WAY2", "찌기");
        raw.put("RCP_PARTS_DTLS", "연두부 75g(3/4모)");
        raw.put("INFO_ENG", "220");
        raw.put("RCP_NA_TIP", "");
        return raw;
    }
}
