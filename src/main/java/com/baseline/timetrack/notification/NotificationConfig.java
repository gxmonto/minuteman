package com.baseline.timetrack.notification;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties;

@Configuration
public class NotificationConfig {

    @Bean
    PushSender pushSender(AppProperties props) {
        var push = props.push();
        PushSender expo = push.expo().enabled()
                ? new ExpoPushSender(push.expo().accessToken(), restClient())
                : new LoggingPushSender();
        PushSender fcm = push.fcm().enabled()
                ? new FcmPushSender(push.fcm().projectId(), push.fcm().credentialsFile(), restClient())
                : new LoggingPushSender();
        return new RoutingPushSender(expo, fcm);
    }

    private static RestClient.Builder restClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory);
    }
}
