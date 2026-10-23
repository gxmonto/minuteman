package com.baseline.timetrack.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * Expo push service, used by the Minuteman mobile app. Expo forwards to FCM (Android) and APNs (iOS)
 * with the credentials configured in the app's EAS project, so the backend needs no Firebase or Apple keys.
 */
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    static final String SEND_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient client;

    public ExpoPushSender(String accessToken, RestClient.Builder builder) {
        if (StringUtils.hasText(accessToken)) {
            // Only needed when "enhanced push security" is turned on for the Expo project.
            builder = builder.defaultHeader("Authorization", "Bearer " + accessToken);
        }
        this.client = builder.build();
    }

    public static boolean handles(String deviceToken) {
        return deviceToken.startsWith("ExponentPushToken[") || deviceToken.startsWith("ExpoPushToken[");
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", deviceToken);
        message.put("title", title);
        message.put("body", body);
        message.put("data", data);
        message.put("sound", "default");
        message.put("priority", "high");
        // Matches the Android channel the app creates (mobile/src/lib/notifications.ts).
        message.put("channelId", "reminders");
        try {
            JsonNode response = client.post()
                    .uri(SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode ticket = response == null ? null : response.path("data");
            if (ticket != null && ticket.isArray()) {
                ticket = ticket.isEmpty() ? null : ticket.get(0);
            }
            if (ticket != null && "ok".equals(ticket.path("status").asString(""))) {
                return Result.SENT;
            }
            if (ticket != null && "DeviceNotRegistered".equals(ticket.path("details").path("error").asString(""))) {
                return Result.INVALID_TOKEN;
            }
            log.warn("Expo push rejected: {}", response);
            return Result.FAILED;
        } catch (RestClientException e) {
            log.warn("Expo push failed: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}
