package com.baseline.timetrack.integration.bitrix;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.baseline.timetrack.config.AppProperties.Integrations.Bitrix24;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.integration.ConnectorException;
import com.baseline.timetrack.integration.ExternalTask;
import com.baseline.timetrack.integration.IntegrationConnector;
import com.baseline.timetrack.integration.TimeEntryExport;

import tools.jackson.databind.JsonNode;

/**
 * Bitrix24 through an inbound REST webhook. Reads tasks with {@code tasks.task.list} and writes
 * elapsed time with {@code task.elapseditem.add}, so hours show up in the task's native time log.
 *
 * <p>The webhook needs the "tasks" scope, and its user needs rights to add elapsed time for others.
 * Bitrix tasks have no coordinates: put the site name/code in a task user field ({@code site-field})
 * and set the geofence once per site from the admin API.
 */
public class Bitrix24Connector implements IntegrationConnector {

    public static final String PROVIDER = "bitrix24";

    private static final int MAX_PAGES = 100;

    private final Bitrix24 props;
    private final RestClient client;
    private final String baseUrl;

    public Bitrix24Connector(Bitrix24 props, RestClient.Builder builder) {
        if (!StringUtils.hasText(props.webhookUrl())) {
            throw new IllegalStateException("timetrack.integrations.bitrix24.webhook-url is required");
        }
        this.props = props;
        this.client = builder.build();
        this.baseUrl = props.webhookUrl().endsWith("/") ? props.webhookUrl() : props.webhookUrl() + "/";
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<ExternalTask> fetchOpenTasks(String externalUserId) {
        List<String> select = new ArrayList<>(List.of(
                "ID", "TITLE", "DESCRIPTION", "STATUS", "REAL_STATUS", "DEADLINE", "RESPONSIBLE_ID", "ACCOMPLICES"));
        if (StringUtils.hasText(props.siteField())) {
            select.add(props.siteField());
        }

        List<ExternalTask> result = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> body = new LinkedHashMap<>();
            // REAL_STATUS < 5: new, pending, in progress, awaiting control (5 = completed, 6 = deferred, 7 = declined).
            body.put("filter", Map.of("RESPONSIBLE_ID", externalUserId, "<REAL_STATUS", 5));
            body.put("select", select);
            body.put("order", Map.of("ID", "asc"));
            body.put("start", start);

            JsonNode response = call("tasks.task.list", body);
            for (JsonNode task : response.path("result").path("tasks")) {
                ExternalTask mapped = map(task);
                if (mapped.status() == null || mapped.status().isWorkable()) {
                    result.add(mapped);
                }
            }
            JsonNode next = response.get("next");
            if (next == null || next.isNull() || !next.canConvertToInt()) {
                return result;
            }
            start = next.asInt();
        }
        return result;
    }

    ExternalTask map(JsonNode task) {
        Set<String> assignees = new LinkedHashSet<>();
        String responsible = text(task, "responsibleId");
        if (responsible != null) {
            assignees.add(responsible);
        }
        task.path("accomplices").forEach(a -> {
            if (StringUtils.hasText(a.asString())) {
                assignees.add(a.asString());
            }
        });

        ExternalTask.ExternalSite site = null;
        if (StringUtils.hasText(props.siteField())) {
            String siteValue = fieldValue(task, props.siteField());
            if (siteValue != null) {
                site = new ExternalTask.ExternalSite(siteValue, siteValue, null, null, null, null);
            }
        }

        String status = text(task, "realStatus") != null ? text(task, "realStatus") : text(task, "status");
        return new ExternalTask(
                text(task, "id"),
                text(task, "title"),
                text(task, "description"),
                mapStatus(status),
                parseDate(text(task, "deadline")),
                List.copyOf(assignees),
                site);
    }

    static TaskStatus mapStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "3" -> TaskStatus.IN_PROGRESS;
            case "4", "5" -> TaskStatus.DONE;
            case "7" -> TaskStatus.CANCELLED;
            default -> TaskStatus.OPEN;
        };
    }

    @Override
    public boolean supportsTimeEntryPush() {
        return props.pushTimeEntries();
    }

    @Override
    public Optional<String> pushTimeEntry(TimeEntryExport entry) {
        if (entry.durationSeconds() <= 0) {
            return Optional.empty();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("SECONDS", entry.durationSeconds());
        fields.put("COMMENT_TEXT", StringUtils.hasText(entry.notes()) ? entry.notes() : "Minuteman #" + entry.id());
        fields.put("USER_ID", entry.userExternalId());
        JsonNode response = call("task.elapseditem.add", Map.of("TASKID", entry.taskExternalId(), "ARFIELDS", fields));
        JsonNode id = response.get("result");
        return id == null || id.isNull() ? Optional.empty() : Optional.of(id.asString());
    }

    private JsonNode call(String method, Map<String, Object> body) {
        JsonNode response;
        try {
            response = client.post()
                    .uri(baseUrl + method + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new ConnectorException("Bitrix24 " + method + " returned " + res.getStatusCode() + ": " + errorBody);
                    })
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ConnectorException("Bitrix24 " + method + " failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new ConnectorException("Bitrix24 " + method + " returned an empty body");
        }
        if (response.hasNonNull("error")) {
            throw new ConnectorException("Bitrix24 " + method + ": " + response.path("error").asString() + " "
                    + response.path("error_description").asString(""));
        }
        return response;
    }

    /** tasks.task.list returns camelCase keys (UF_AUTO_1 -> ufAuto1); accept either spelling. */
    private static String fieldValue(JsonNode task, String field) {
        String value = text(task, field);
        return value != null ? value : text(task, toCamelCase(field));
    }

    static String toCamelCase(String upperSnake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : upperSnake.toLowerCase().toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isArray()) {
            value = value.isEmpty() ? null : value.get(0);
            if (value == null) {
                return null;
            }
        }
        String text = value.asString();
        return StringUtils.hasText(text) ? text : null;
    }

    private static Instant parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
