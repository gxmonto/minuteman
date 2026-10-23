package com.baseline.timetrack.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties;

@Configuration
public class NotificationConfig {

    @Bean
    PushSender pushSender(AppProperties props) {
        var fcm = props.push().fcm();
        if (!fcm.enabled()) {
            return new LoggingPushSender();
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return new FcmPushSender(fcm.projectId(), fcm.credentialsFile(), RestClient.builder().requestFactory(factory));
    }
}
