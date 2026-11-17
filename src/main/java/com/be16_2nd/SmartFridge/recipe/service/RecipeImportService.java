package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.recipe.dto.RecipeImportResDto;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceClient;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceProperties;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공공데이터 레시피를 처음부터 끝까지 받아 DB에 넣는다.
 * 원본 번호로 덮어쓰므로 여러 번 실행해도 레시피가 중복되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeImportService {

    static final int UNKNOWN_LIMIT = 50;

    private final RecipeSourceClient sourceClient;
    private final RecipeSourceProperties properties;
    private final RecipeImporter importer;

    // 동시에 두 번 실행되면 같은 레시피를 서로 넣으려다 유니크 제약에 걸리므로 하나만 돌게 한다 (서버 한 대 기준)
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RecipeImportResDto importAll() {
        if (!running.compareAndSet(false, true)) {
            throw new RecipeImportInProgressException();
        }
        try {
            return doImport();
        } finally {
            running.set(false);
        }
    }

    private RecipeImportResDto doImport() {
        int pageSize = properties.pageSize();
        boolean sampleKey = properties.isSampleKey();

        // 첫 묶음이 실패하면 전체 건수를 모르므로 그대로 실패로 돌려준다
        RecipeSourceClient.RecipeSourcePage first = sourceClient.fetch(1, pageSize);
        int total = first.totalCount();

        Tally tally = new Tally();
        tally.add(importer.importPage(toRows(first)));

        if (sampleKey) {
            // 시험용 키는 구간과 상관없이 같은 몇 건만 내려주므로 더 받지 않는다
            log.warn("레시피 적재: 시험용 키(sample)라 첫 묶음만 넣었습니다. 환경변수 FOOD_SAFETY_API_KEY 를 설정하세요.");
        } else {
            for (int start = pageSize + 1; start <= total; start += pageSize) {
                int end = Math.min(start + pageSize - 1, total);
                try {
                    tally.add(importer.importPage(toRows(sourceClient.fetch(start, end))));
                } catch (RuntimeException e) {
                    log.error("레시피 적재 실패 {}~{}: {}", start, end, e.getMessage());
                    tally.failedRanges.add(start + "-" + end);
                }
            }
        }

        log.info("레시피 적재 완료 total={} created={} updated={} failed={}",
                total, tally.created, tally.updated, tally.failedRanges);
        return new RecipeImportResDto(total, tally.created, tally.updated, tally.failedRanges,
                topUnknown(tally.unknownNames), sampleKey);
    }

    private static List<RecipeSourceRow> toRows(RecipeSourceClient.RecipeSourcePage page) {
        List<RecipeSourceRow> rows = new ArrayList<>();
        for (Map<String, String> raw : page.rows()) {
            try {
                rows.add(RecipeSourceRow.from(raw));
            } catch (IllegalArgumentException e) {
                // 번호·이름이 없는 행은 넣을 수 없으니 건너뛴다
                log.warn("레시피 행 건너뜀: {}", e.getMessage());
            }
        }
        return rows;
    }

    private static List<RecipeImportResDto.UnknownIngredient> topUnknown(List<String> names) {
        return names.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(UNKNOWN_LIMIT)
                .map(e -> new RecipeImportResDto.UnknownIngredient(e.getKey(), e.getValue()))
                .toList();
    }

    private static class Tally {
        int created;
        int updated;
        final List<String> failedRanges = new ArrayList<>();
        final List<String> unknownNames = new ArrayList<>();

        void add(RecipeImporter.PageResult result) {
            created += result.created();
            updated += result.updated();
            unknownNames.addAll(result.unknownNames());
        }
    }
}
