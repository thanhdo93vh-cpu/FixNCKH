package com.example.hauiTrash.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

//    @Bean
//    public WebClient geminiWebClient(GeminiProperties props) {
//        return WebClient.builder()
//                .baseUrl(props.getBaseUrl())
//                .build();
//    }
}