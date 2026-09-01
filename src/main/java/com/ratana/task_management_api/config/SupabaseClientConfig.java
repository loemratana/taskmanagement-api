package com.ratana.task_management_api.config;


import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
@RequiredArgsConstructor
public class SupabaseClientConfig {

    @Bean
    public RestClient supabaseRestClient(SupabaseProperties properties)
    {
        return RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("Authorization", "Bearer " + properties.serviceKey())
                .defaultHeader("apikey", properties.serviceKey())
                .build();


    }
}
