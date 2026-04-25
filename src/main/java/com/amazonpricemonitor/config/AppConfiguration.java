package com.amazonpricemonitor.config;

import com.amazonpricemonitor.service.ai.GeminiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
    AlterLabProperties.class,
    NotificationProperties.class,
    JsoupClientProperties.class,
    SchedulerProperties.class,
    GeminiProperties.class
})
public class AppConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Dedicated {@code RestClient} for {@code GeminiClient}. Configured with a hard
     * connect+read timeout so the alert path remains bounded even when Gemini is slow.
     * Built from a {@link RestClient.Builder#clone() clone} of the shared builder so we
     * do not mutate the request factory used by other notifiers (e.g. Slack).
     */
    @Bean(name = "geminiRestClient")
    RestClient geminiRestClient(GeminiProperties geminiProperties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(geminiProperties.getTimeoutMs());
        factory.setReadTimeout(geminiProperties.getTimeoutMs());
        return restClientBuilder.clone().requestFactory(factory).build();
    }

    @Bean(name = "priceCheckTaskScheduler")
    ThreadPoolTaskScheduler priceCheckTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("price-check-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
