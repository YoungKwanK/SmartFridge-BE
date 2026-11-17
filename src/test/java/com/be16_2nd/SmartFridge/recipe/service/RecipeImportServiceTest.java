package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.recipe.dto.RecipeImportResDto;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceClient;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceClient.RecipeSourcePage;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceException;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 적재 흐름(페이지 나누기, 실패 구간 기록, 사전에 없는 재료 집계) 단위 테스트. DB·외부 호출 없음.
 */
class RecipeImportServiceTest {

    final RecipeSourceClient client = mock(RecipeSourceClient.class);
    final RecipeImporter importer = mock(RecipeImporter.class);

    @Test
    void 전체_건수만큼_페이지를_나눠_받는다() {
        RecipeImportService service = service("real-key");
        when(client.fetch(1, 100)).thenReturn(page(250, "1"));
        when(client.fetch(101, 200)).thenReturn(page(250, "101"));
        when(client.fetch(201, 250)).thenReturn(page(250, "201"));
        when(importer.importPage(anyList()))
                .thenReturn(new RecipeImporter.PageResult(1, 0, List.of("조선간장")))
                .thenReturn(new RecipeImporter.PageResult(0, 1, List.of("조선간장", "알룰로스")))
                .thenReturn(new RecipeImporter.PageResult(1, 0, List.of()));

        RecipeImportResDto result = service.importAll();

        assertThat(result.totalCount()).isEqualTo(250);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failedRanges()).isEmpty();
        assertThat(result.sampleKey()).isFalse();
        assertThat(result.unknownIngredients()).containsExactly(
                new RecipeImportResDto.UnknownIngredient("조선간장", 2),
                new RecipeImportResDto.UnknownIngredient("알룰로스", 1));
    }

    @Test
    void 중간_페이지가_실패해도_나머지는_넣고_실패_구간을_알려준다() {
        RecipeImportService service = service("real-key");
        when(client.fetch(1, 100)).thenReturn(page(300, "1"));
        when(client.fetch(101, 200)).thenThrow(new RecipeSourceException("시간 초과"));
        when(client.fetch(201, 300)).thenReturn(page(300, "201"));
        when(importer.importPage(anyList())).thenReturn(new RecipeImporter.PageResult(1, 0, List.of()));

        RecipeImportResDto result = service.importAll();

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failedRanges()).containsExactly("101-200");
    }

    @Test
    void 첫_페이지가_실패하면_전체가_실패한다() {
        RecipeImportService service = service("real-key");
        when(client.fetch(1, 100)).thenThrow(new RecipeSourceException("인증키 오류"));

        assertThatThrownBy(service::importAll).isInstanceOf(RecipeSourceException.class);
        verify(importer, never()).importPage(anyList());
    }

    @Test
    void 시험용_키면_첫_페이지만_넣는다() {
        RecipeImportService service = service("sample");
        when(client.fetch(1, 100)).thenReturn(page(1156, "28"));
        when(importer.importPage(anyList())).thenReturn(new RecipeImporter.PageResult(5, 0, List.of()));

        RecipeImportResDto result = service.importAll();

        assertThat(result.sampleKey()).isTrue();
        assertThat(result.created()).isEqualTo(5);
        verify(client, never()).fetch(101, 200);
    }

    @Test
    void 번호나_이름이_없는_행은_건너뛴다() {
        RecipeImportService service = service("real-key");
        RecipeSourcePage page = new RecipeSourcePage(2, List.of(
                Map.of("RCP_SEQ", "1", "RCP_NM", "계란찜"),
                Map.of("RCP_SEQ", "", "RCP_NM", "번호 없음")));
        when(client.fetch(anyInt(), anyInt())).thenReturn(page);
        when(importer.importPage(anyList())).thenAnswer(invocation -> {
            List<?> rows = invocation.getArgument(0);
            return new RecipeImporter.PageResult(rows.size(), 0, List.of());
        });

        assertThat(service.importAll().created()).isEqualTo(1);
    }

    private RecipeImportService service(String apiKey) {
        RecipeSourceProperties properties = new RecipeSourceProperties("http://localhost", apiKey, 100, Duration.ofSeconds(5));
        return new RecipeImportService(client, properties, importer);
    }

    private static RecipeSourcePage page(int total, String seq) {
        return new RecipeSourcePage(total, List.of(Map.of("RCP_SEQ", seq, "RCP_NM", "레시피" + seq)));
    }
}
