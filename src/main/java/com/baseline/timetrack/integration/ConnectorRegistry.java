package com.baseline.timetrack.integration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.integration.http.HttpConnector;

/** Every configured connector, keyed by provider. */
@Component
public class ConnectorRegistry {

    /** Source of tasks and sites created by hand in Minuteman. */
    public static final String LOCAL = "local";

    private static final Pattern PROVIDER_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final Map<String, IntegrationConnector> connectors = new LinkedHashMap<>();

    public ConnectorRegistry(List<IntegrationConnector> connectorBeans, AppProperties props) {
        connectorBeans.forEach(this::register);
        props.integrations().http().forEach((name, httpProps) -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(httpProps.timeout());
            factory.setReadTimeout(httpProps.timeout());
            register(new HttpConnector(name, httpProps, RestClient.builder().requestFactory(factory)));
        });
        props.integrations().inboundApiKeys().keySet().forEach(ConnectorRegistry::validateKey);
    }

    private void register(IntegrationConnector connector) {
        validateKey(connector.provider());
        if (connectors.putIfAbsent(connector.provider(), connector) != null) {
            throw new IllegalStateException("Duplicate integration provider: " + connector.provider());
        }
    }

    public static void validateKey(String provider) {
        if (!PROVIDER_KEY.matcher(provider).matches() || LOCAL.equals(provider)) {
            throw new IllegalStateException("Invalid provider key '" + provider
                    + "': use lowercase letters, digits, '-' or '_', and not '" + LOCAL + "'");
        }
    }

    public Optional<IntegrationConnector> find(String provider) {
        return Optional.ofNullable(connectors.get(provider));
    }

    public Collection<IntegrationConnector> all() {
        return Collections.unmodifiableCollection(connectors.values());
    }

    public boolean canPushTimeEntries(String provider) {
        return find(provider).map(IntegrationConnector::supportsTimeEntryPush).orElse(false);
    }
}
