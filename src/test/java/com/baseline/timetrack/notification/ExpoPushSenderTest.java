package com.baseline.timetrack.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushSenderTest {

    private static final String TOKEN = "ExponentPushToken[abc123]";

    private MockRestServiceServer server;
    private ExpoPushSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ExpoPushSender("expo-secret", builder);
    }

    @Test
    void sendsMessage() {
        server.expect(requestTo(ExpoPushSender.SEND_URL))
                .andExpect(header("Authorization", "Bearer expo-secret"))
                .andExpect(content().json("""
                        {"to":"ExponentPushToken[abc123]","title":"T","body":"B","data":{"type":"LEFT_SITE"},
                         "channelId":"reminders","priority":"high"}
                        """))
                .andRespond(withSuccess("{\"data\":{\"status\":\"ok\",\"id\":\"x\"}}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(TOKEN, "T", "B", Map.of("type", "LEFT_SITE"))).isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void detectsUnregisteredDevices() {
        server.expect(requestTo(ExpoPushSender.SEND_URL)).andRespond(withSuccess("""
                {"data":[{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}]}
                """, MediaType.APPLICATION_JSON));
        assertThat(sender.send(TOKEN, "T", "B", Map.of())).isEqualTo(PushSender.Result.INVALID_TOKEN);
    }

    @Test
    void reportsOtherFailures() {
        server.expect(requestTo(ExpoPushSender.SEND_URL)).andRespond(withServerError());
        assertThat(sender.send(TOKEN, "T", "B", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }

    @Test
    void routesByTokenType() {
        PushSender expo = (t, title, body, data) -> PushSender.Result.SENT;
        PushSender fcm = (t, title, body, data) -> PushSender.Result.FAILED;
        RoutingPushSender routing = new RoutingPushSender(expo, fcm);
        assertThat(routing.send(TOKEN, "T", "B", Map.of())).isEqualTo(PushSender.Result.SENT);
        assertThat(routing.send("fcm-raw-token", "T", "B", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }
}
