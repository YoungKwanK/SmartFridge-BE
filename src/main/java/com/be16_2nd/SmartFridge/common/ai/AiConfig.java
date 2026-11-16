package com.be16_2nd.SmartFridge.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    // 클라이언트는 스레드 안전하고 커넥션 풀을 가지므로 애플리케이션 전체에서 하나만 쓴다
    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(AiProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())   // 429·5xx·네트워크 오류는 SDK가 재시도
                .build();
    }
}
