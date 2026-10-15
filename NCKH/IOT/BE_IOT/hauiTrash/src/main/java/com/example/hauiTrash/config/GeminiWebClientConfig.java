package com.example.hauiTrash.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
@RequiredArgsConstructor
public class GeminiWebClientConfig {

    private final GeminiProperties props;

    @Bean
    public WebClient geminiWebClient() {

        int timeout = Math.max(props.getTimeoutSeconds(), 10);

        HttpClient httpClient = HttpClient.create()
                // timeout connect
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // timeout tổng thời gian chờ response (headers/body)
                .responseTimeout(Duration.ofSeconds(timeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout))
                        .addHandlerLast(new WriteTimeoutHandler(timeout))
                );

        // nếu response có thể hơi lớn, tăng buffer (mặc định 256KB)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
                .build();

        return WebClient.builder()
                .baseUrl(props.getBaseUrl()) // vd: https://generativelanguage.googleapis.com
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
