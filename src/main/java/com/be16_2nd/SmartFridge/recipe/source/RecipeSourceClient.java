package com.be16_2nd.SmartFridge.recipe.source;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 식품안전나라 조리식품 레시피 DB(COOKRCP01) 호출
 * GET {baseUrl}/{apiKey}/COOKRCP01/json/{start}/{end}  (start, end 는 1부터 시작하는 건 번호)
 *
 * 응답 Content-Type 이 text/html 로 오므로 문자열로 받아 직접 JSON 으로 읽는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(RecipeSourceProperties.class)
public class RecipeSourceClient {

    private static final String SERVICE = "COOKRCP01";
    private static final String SUCCESS = "INFO-000";
    private static final String NO_DATA = "INFO-200";

    private final RecipeSourceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeSourceClient(RecipeSourceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public RecipeSourcePage fetch(int start, int end) {
        String body;
        try {
            body = restClient.get()
                    .uri("/{key}/{service}/json/{start}/{end}", properties.effectiveApiKey(), SERVICE, start, end)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 예외 메시지에 인증키가 든 URL 이 섞일 수 있어 원인 메시지는 그대로 내보내지 않는다
            log.error("레시피 공공데이터 호출 실패 {}~{}: {}", start, end, e.getClass().getSimpleName());
            throw new RecipeSourceException("레시피 공공데이터를 받지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        }
        return parse(body, start, end);
    }

    RecipeSourcePage parse(String body, int start, int end) {
        JsonNode service;
        try {
            service = objectMapper.readTree(body == null ? "" : body).path(SERVICE);
        } catch (JacksonException e) {
            throw new RecipeSourceException("레시피 공공데이터 응답 형식이 올바르지 않습니다.", e);
        }
        if (service.isMissingNode()) {
            throw new RecipeSourceException("레시피 공공데이터 응답 형식이 올바르지 않습니다.");
        }

        String code = service.path("RESULT").path("CODE").asString("");
        if (NO_DATA.equals(code)) {
            return new RecipeSourcePage(0, List.of());
        }
        if (!SUCCESS.equals(code)) {
            String message = service.path("RESULT").path("MSG").asString("");
            log.error("레시피 공공데이터 오류 {}~{} code={} msg={}", start, end, code, message);
            throw new RecipeSourceException("레시피 공공데이터 오류(" + code + "): " + message);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode node : service.path("row")) {
            Map<String, String> row = new LinkedHashMap<>();
            node.properties().forEach(entry -> row.put(entry.getKey(), entry.getValue().asString("")));
            rows.add(row);
        }
        return new RecipeSourcePage(parseTotal(service.path("total_count").asString("")), rows);
    }

    private static int parseTotal(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @param totalCount 전체 레시피 수
     * @param rows       이번에 받은 레시피 (원본 필드 이름 → 값)
     */
    public record RecipeSourcePage(int totalCount, List<Map<String, String>> rows) {
    }
}
