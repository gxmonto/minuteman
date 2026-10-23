package com.baseline.timetrack.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class FirstRunSetupTest {

    @Configuration
    static class Empty {
    }

    @TempDir
    Path dir;

    @Test
    void onlyRunsWhenNothingIsConfigured() throws Exception {
        Path env = dir.resolve(".env");
        assertThat(FirstRunSetup.needsSetup(new String[0], Map.of(), env)).isTrue();
        assertThat(FirstRunSetup.needsSetup(new String[0], Map.of("DB_PASSWORD", "x"), env)).isFalse();
        assertThat(FirstRunSetup.needsSetup(new String[] {"--spring.datasource.password=x"}, Map.of(), env)).isFalse();
        Files.writeString(env, "DB_PASSWORD=x\n");
        assertThat(FirstRunSetup.needsSetup(new String[0], Map.of(), env)).isFalse();
    }

    @Test
    void springReadsTheGeneratedFileExactly() throws Exception {
        Path env = dir.resolve(".env");
        String tricky = " p@ss\\w:rd=#!ñ";
        Map<String, String> values = new LinkedHashMap<>();
        values.put("DB_PASSWORD", tricky);
        values.put("JWT_SECRET", FirstRunSetup.randomSecret());
        FirstRunSetup.write(env, values);

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=optional:file:" + env + "[.properties]",
                        "spring.main.banner-mode=off")
                .run()) {
            assertThat(ctx.getEnvironment().getProperty("DB_PASSWORD")).isEqualTo(tricky);
            assertThat(ctx.getEnvironment().getProperty("JWT_SECRET")).hasSizeGreaterThanOrEqualTo(32);
        }
        if (env.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(env)).hasSize(2);
        }
    }
}
