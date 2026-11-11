package com.be16_2nd.SmartFridge.chat.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 채팅 목록 응답의 JSON 키 확인.
 * 프론트는 joinStatus(소문자 j)로 열림/닫힘을 판단하므로 키가 JoinStatus면 모든 방이 닫힘으로 보인다.
 */
class MyChatListResDtoJsonTest {

    @Test
    void 모집_상태는_joinStatus_키로_내려간다() {
        JsonNode json = JsonMapper.builder().build()
                .valueToTree(MyChatListResDto.builder().roomId(3L).joinStatus("OPEN").build());

        assertThat(json.get("joinStatus").asString()).isEqualTo("OPEN");
        assertThat(json.has("JoinStatus")).isFalse();
    }
}
