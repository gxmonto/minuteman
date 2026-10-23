package com.baseline.timetrack.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    RecordingPushSender recordingPushSender() {
        return new RecordingPushSender();
    }

    @Bean
    FakeConnector fakeConnector() {
        return new FakeConnector();
    }
}
