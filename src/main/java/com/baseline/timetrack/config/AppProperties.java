package com.baseline.timetrack.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("timetrack")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue Reminders reminders,
        @DefaultValue Sites sites,
        @DefaultValue Push push,
        @DefaultValue Integrations integrations,
        @DefaultValue Cors cors,
        @DefaultValue("es") Locale locale) {

    /** Browser origins allowed to call the API, e.g. the web build of the mobile app. Empty: none. */
    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Jwt(
            String secret,
            @DefaultValue("timetrack") String issuer,
            @DefaultValue("PT12H") Duration ttl) {
    }

    /** Creates the first admin when the users table is empty. */
    public record BootstrapAdmin(String email, String password) {
    }

    public record Reminders(
            /** Remind to stop a timer that has been running this long... */
            @DefaultValue("PT4H") Duration timerOpenAfter,
            /** ...or to clock out of a day open this long... */
            @DefaultValue("PT12H") Duration workDayOpenAfter,
            /** ...and repeat at most this often. */
            @DefaultValue("PT1H") Duration repeatEvery,
            /** Minimum time between "you left the site" notifications for the same timer. */
            @DefaultValue("PT15M") Duration leftSiteRepeatEvery,
            /** Extra slack on top of the site radius before someone counts as gone (GPS drift). */
            @DefaultValue("50") int leftSiteToleranceMeters) {
    }

    public record Sites(@DefaultValue("150") int defaultRadiusMeters) {
    }

    public record Push(@DefaultValue Expo expo, @DefaultValue Fcm fcm) {

        /** Expo push service, used by the mobile app's tokens. */
        public record Expo(
                @DefaultValue("true") boolean enabled,
                /** Optional; required only if "enhanced push security" is on in the Expo project. */
                String accessToken) {
        }

        public record Fcm(
                @DefaultValue("false") boolean enabled,
                String projectId,
                /** Path to the Firebase service account JSON. */
                String credentialsFile) {
        }
    }

    public record Integrations(
            @DefaultValue("10") int maxPushAttempts,
            /** provider -> API key for systems that push tasks / pull time entries through /api/integrations/inbound. */
            Map<String, String> inboundApiKeys,
            /** name -> generic HTTP connector; the name becomes the provider key. */
            Map<String, HttpConnectorProperties> http,
            @DefaultValue Bitrix24 bitrix24) {

        public Integrations {
            inboundApiKeys = inboundApiKeys == null ? Map.of() : Map.copyOf(inboundApiKeys);
            http = http == null ? Map.of() : Map.copyOf(http);
        }

        public record HttpConnectorProperties(
                /** GET, called with ?externalUserId=..., returns a JSON array of tasks. */
                String tasksUrl,
                /** Optional POST target for closed time entries. */
                String timeEntriesUrl,
                /** Static headers sent on every call, e.g. Authorization. */
                Map<String, String> headers,
                @DefaultValue("PT20S") Duration timeout) {

            public HttpConnectorProperties {
                headers = headers == null ? Map.of() : Map.copyOf(headers);
            }
        }

        public record Bitrix24(
                @DefaultValue("false") boolean enabled,
                /** Inbound webhook base URL, e.g. https://acme.bitrix24.com/rest/1/abc123/ */
                String webhookUrl,
                /** Optional task user field (e.g. UF_AUTO_123456) holding the site name/code. */
                String siteField,
                @DefaultValue("true") boolean pushTimeEntries,
                @DefaultValue("PT20S") Duration timeout) {
        }
    }
}
