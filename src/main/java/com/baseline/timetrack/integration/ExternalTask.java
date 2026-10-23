package com.baseline.timetrack.integration;

import java.time.Instant;
import java.util.List;

import com.baseline.timetrack.entity.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Provider-neutral task, as returned by connectors and as accepted by the inbound API.
 *
 * @param assigneeExternalUserIds external user ids; {@code null} leaves current assignees untouched
 * @param site where the work happens; {@code null} keeps whatever site the task already has
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalTask(
        @NotBlank @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String title,
        String description,
        TaskStatus status,
        Instant dueAt,
        List<@NotBlank String> assigneeExternalUserIds,
        @Valid ExternalSite site) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalSite(
            @NotBlank @Size(max = 255) String externalId,
            @Size(max = 255) String name,
            @Size(max = 500) String address,
            Double latitude,
            Double longitude,
            Integer radiusMeters) {
    }
}
