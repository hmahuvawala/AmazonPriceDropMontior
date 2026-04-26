package com.amazonpricemonitor.config;

import com.amazonpricemonitor.service.ai.GeminiProperties;
import com.amazonpricemonitor.service.notify.EmailNotifier;
import com.amazonpricemonitor.service.notify.SmsNotifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
    AdminProperties.class,
    AlterLabProperties.class,
    EmailProperties.class,
    SmsProperties.class,
    JsoupClientProperties.class,
    SchedulerProperties.class,
    GeminiProperties.class
})
public class AppConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
    public EmailNotifier emailNotifier(EmailProperties emailProperties, JavaMailSender javaMailSender) {
        return new EmailNotifier(emailProperties, javaMailSender);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Dedicated {@code RestClient} for {@code GeminiClient}. Configured with a hard
     * connect+read timeout so the alert path remains bounded even when Gemini is slow.
     * Built from a {@link RestClient.Builder#clone() clone} of the shared builder so we
     * do not mutate the request factory used by other clients.
     */
    @Bean(name = "geminiRestClient")
    RestClient geminiRestClient(GeminiProperties geminiProperties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(geminiProperties.getTimeoutMs());
        factory.setReadTimeout(geminiProperties.getTimeoutMs());
        return restClientBuilder.clone().requestFactory(factory).build();
    }

    /**
     * Dedicated {@code RestClient} for Twilio SMS. Hard connect+read timeout so alert
     * transactions stay bounded.
     */
    @Bean(name = "twilioRestClient")
    RestClient twilioRestClient(SmsProperties smsProperties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(smsProperties.getTimeoutMs());
        factory.setReadTimeout(smsProperties.getTimeoutMs());
        return restClientBuilder.clone().requestFactory(factory).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.notification.sms.enabled", havingValue = "true")
    public SmsNotifier smsNotifier(
            SmsProperties smsProperties, @Qualifier("twilioRestClient") RestClient twilioRestClient) {
        return new SmsNotifier(smsProperties, twilioRestClient);
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
