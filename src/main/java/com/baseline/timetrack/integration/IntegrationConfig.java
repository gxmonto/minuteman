package com.baseline.timetrack.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.integration.bitrix.Bitrix24Connector;

@Configuration
public class IntegrationConfig {

    @Bean
    @ConditionalOnProperty(name = "timetrack.integrations.bitrix24.enabled", havingValue = "true")
    Bitrix24Connector bitrix24Connector(AppProperties props) {
        var bitrix = props.integrations().bitrix24();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(bitrix.timeout());
        factory.setReadTimeout(bitrix.timeout());
        return new Bitrix24Connector(bitrix, RestClient.builder().requestFactory(factory));
    }
}
