package com.be16_2nd.SmartFridge.recipe.ingredient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngredientDictionaryTest {

    final IngredientDictionary dictionary = IngredientDictionary.of("""
            # 주석은 무시한다
            @기본양념 소금, 간장

            간장 = 저염간장, 진간장
            계란 = 달걀, 달걀 노른자
            """);

    @Test
    void 별칭은_대표이름으로_바꾼다() {
        assertThat(dictionary.resolve("달걀"))
                .isEqualTo(new IngredientDictionary.Resolved("계란", false, true));
    }

    @Test
    void 띄어쓰기는_무시하고_비교한다() {
        assertThat(dictionary.resolve("달걀노른자").name()).isEqualTo("계란");
        assertThat(dictionary.resolve("저염 간장").name()).isEqualTo("간장");
    }

    @Test
    void 기본양념이면_pantry() {
        assertThat(dictionary.resolve("진간장"))
                .isEqualTo(new IngredientDictionary.Resolved("간장", true, true));
        assertThat(dictionary.resolve("소금").pantry()).isTrue();
    }

    @Test
    void 사전에_없으면_띄어쓰기를_빼고_소문자로_맞춰_known_false() {
        assertThat(dictionary.resolve("방울 토마토"))
                .isEqualTo(new IngredientDictionary.Resolved("방울토마토", false, false));
        assertThat(dictionary.resolve("MSG").name()).isEqualTo("msg");
    }

    @Test
    void 한_이름이_두_대표이름에_들어가면_읽을_때_실패한다() {
        assertThatThrownBy(() -> IngredientDictionary.of("""
                계란 = 달걀
                오리알 = 달걀
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달걀");
    }

    @Test
    void 등호가_없는_줄이면_실패한다() {
        assertThatThrownBy(() -> IngredientDictionary.of("계란 달걀"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 실제_사전_파일을_읽을_수_있다() {
        IngredientDictionary real = new IngredientDictionary();

        assertThat(real.resolve("달걀").name()).isEqualTo("계란");
        assertThat(real.resolve("칵테일새우").name()).isEqualTo("새우");
        assertThat(real.resolve("통깨")).isEqualTo(new IngredientDictionary.Resolved("참깨", true, true));
    }
}
