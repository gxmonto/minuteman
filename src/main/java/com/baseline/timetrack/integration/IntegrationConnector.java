package com.baseline.timetrack.integration;

import java.util.List;
import java.util.Optional;

/**
 * Adapter to an external task system (Bitrix24, a CRM, an ERP, a custom API...).
 *
 * <p>Connectors are pulled by Minuteman. Systems that prefer to push their tasks and pull time
 * entries themselves need no connector: they use the inbound API with an API key instead.
 */
public interface IntegrationConnector {

    /** Unique provider key, stored as the {@code source} of imported tasks and sites. */
    String provider();

    /** Open tasks currently assigned to the given user of the external system. */
    List<ExternalTask> fetchOpenTasks(String externalUserId);

    default boolean supportsTimeEntryPush() {
        return false;
    }

    /**
     * Records a closed time entry in the external system.
     *
     * @return the id the external system gave the entry, if any
     * @throws ConnectorException when the call fails; it will be retried with backoff
     */
    default Optional<String> pushTimeEntry(TimeEntryExport entry) {
        throw new UnsupportedOperationException(provider() + " does not accept time entries");
    }
}
