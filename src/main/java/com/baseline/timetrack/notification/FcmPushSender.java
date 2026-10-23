package com.baseline.timetrack.notification;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.google.auth.oauth2.GoogleCredentials;

/** Firebase Cloud Messaging HTTP v1; works for Android and, through APNs in Firebase, for iOS. */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;
    private final RestClient client;
    private final String sendUrl;

    public FcmPushSender(String projectId, String credentialsFile, RestClient.Builder builder) {
        try (InputStream in = new FileInputStream(credentialsFile)) {
            this.credentials = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read FCM credentials from " + credentialsFile, e);
        }
        this.client = builder.build();
        this.sendUrl = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        Map<String, Object> message = Map.of("message", Map.of(
                "token", deviceToken,
                "notification", Map.of("title", title, "body", body),
                "data", data,
                "android", Map.of("priority", "high"),
                "apns", Map.of("headers", Map.of("apns-priority", "10"))));
        try {
            credentials.refreshIfExpired();
            client.post()
                    .uri(sendUrl)
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getResponseBodyAsString().contains("UNREGISTERED")) {
                return Result.INVALID_TOKEN;
            }
            log.warn("FCM rejected message: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Result.FAILED;
        } catch (IOException | RestClientException e) {
            log.warn("FCM send failed: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}
