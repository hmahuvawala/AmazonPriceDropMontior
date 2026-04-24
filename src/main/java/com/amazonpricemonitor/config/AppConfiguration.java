package com.amazonpricemonitor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
    AlterLabProperties.class,
    SlackProperties.class,
    JsoupClientProperties.class,
    SchedulerProperties.class
})
public class AppConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
