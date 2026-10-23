package com.baseline.timetrack.integration.http;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.baseline.timetrack.config.AppProperties.Integrations.HttpConnectorProperties;
import com.baseline.timetrack.integration.ConnectorException;
import com.baseline.timetrack.integration.ExternalTask;
import com.baseline.timetrack.integration.IntegrationConnector;
import com.baseline.timetrack.integration.TimeEntryExport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic connector for any system that can expose two small HTTP endpoints:
 * <ul>
 *   <li>{@code GET tasks-url?externalUserId=...} returning {@code [ExternalTask...]}</li>
 *   <li>optionally {@code POST time-entries-url} accepting a {@link TimeEntryExport}, answering {@code {"id": "..."}} or nothing</li>
 * </ul>
 * Usually that is a thin adapter or an automation (n8n, Zapier, a serverless function) in front of the real system.
 */
public class HttpConnector implements IntegrationConnector {

    private static final ParameterizedTypeReference<List<ExternalTask>> TASK_LIST = new ParameterizedTypeReference<>() {
    };

    private final String provider;
    private final HttpConnectorProperties props;
    private final RestClient client;

    public HttpConnector(String provider, HttpConnectorProperties props, RestClient.Builder builder) {
        if (!StringUtils.hasText(props.tasksUrl())) {
            throw new IllegalStateException("timetrack.integrations.http." + provider + ".tasks-url is required");
        }
        this.provider = provider;
        this.props = props;
        this.client = builder.defaultHeaders(h -> props.headers().forEach(h::set)).build();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public List<ExternalTask> fetchOpenTasks(String externalUserId) {
        URI uri = UriComponentsBuilder.fromUriString(props.tasksUrl())
                .queryParam("externalUserId", externalUserId)
                .encode()
                .build()
                .toUri();
        try {
            List<ExternalTask> tasks = client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(TASK_LIST);
            return tasks == null ? List.of() : tasks;
        } catch (RestClientException e) {
            throw new ConnectorException(provider + ": fetching tasks failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsTimeEntryPush() {
        return StringUtils.hasText(props.timeEntriesUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushResponse(String id) {
    }

    @Override
    public Optional<String> pushTimeEntry(TimeEntryExport entry) {
        try {
            PushResponse response = client.post()
                    .uri(URI.create(props.timeEntriesUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(entry)
                    .retrieve()
                    .body(PushResponse.class);
            return Optional.ofNullable(response).map(PushResponse::id);
        } catch (RestClientException e) {
            throw new ConnectorException(provider + ": pushing time entry failed: " + e.getMessage(), e);
        }
    }
}
