package com.baseline.timetrack.setup;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asks for the database credentials (and the first admin) the first time the backend runs, and saves
 * them to a git-ignored {@code .env} file that application.properties imports. Nothing secret lives in
 * the repository.
 *
 * <p>Uses only the JDK, so it can also run straight from source before building:
 * {@code java src/main/java/com/baseline/timetrack/setup/FirstRunSetup.java}
 */
public final class FirstRunSetup {

    public static final Path ENV_FILE = Path.of(".env");

    private FirstRunSetup() {
    }

    public static void main(String[] args) throws IOException {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this from an interactive terminal.");
            System.exit(1);
        }
        run(console, ENV_FILE);
    }

    /**
     * Called before Spring starts. Runs the setup when there is no configuration yet (or with
     * {@code --setup}); does nothing in deployments that pass DB_PASSWORD through the environment.
     */
    public static void runIfNeeded(String[] args) throws IOException {
        boolean forced = Arrays.asList(args).contains("--setup");
        if (!forced && !needsSetup(args, System.getenv(), ENV_FILE)) {
            return;
        }
        Console console = System.console();
        if (console == null) {
            System.err.println("""

                    Minuteman is not configured yet (no .env file and no DB_PASSWORD variable).
                    Run the first-time setup from a terminal in the project folder:

                        java src/main/java/com/baseline/timetrack/setup/FirstRunSetup.java

                    or set DB_URL, DB_USERNAME, DB_PASSWORD and JWT_SECRET as environment variables.
                    """);
            System.exit(1);
        }
        run(console, ENV_FILE);
    }

    static boolean needsSetup(String[] args, Map<String, String> env, Path envFile) {
        if (Files.exists(envFile) || env.containsKey("DB_PASSWORD") || env.containsKey("SPRING_DATASOURCE_PASSWORD")) {
            return false;
        }
        return Arrays.stream(args).noneMatch(a -> a.contains("datasource.password") || a.contains("DB_PASSWORD"));
    }

    private static void run(Console console, Path envFile) throws IOException {
        console.printf("%n=== Minuteman: configuración inicial ===%n");
        console.printf("Los datos se guardan en %s (fuera de git). Para cambiarlos después, borra el archivo o arranca con --setup.%n%n",
                envFile.toAbsolutePath());

        String url;
        String user;
        char[] password;
        while (true) {
            url = ask(console, "URL de MySQL", "jdbc:mysql://localhost:3306/timetracker?createDatabaseIfNotExist=true");
            user = ask(console, "Usuario de MySQL", "timetrack");
            password = askSecret(console, "Contraseña de MySQL");
            String problem = testConnection(url, user, password);
            if (problem == null) {
                break;
            }
            console.printf("%nNo se pudo conectar: %s%n", problem);
            if (!ask(console, "¿Guardar de todos modos? (s/n)", "n").toLowerCase().startsWith("s")) {
                console.printf("Intentemos de nuevo.%n%n");
                continue;
            }
            break;
        }

        console.printf("%nPrimer usuario administrador (se crea solo si la base está vacía).%n");
        String adminEmail = ask(console, "Email del admin", "admin@example.com");
        char[] adminPassword;
        while (true) {
            adminPassword = askSecret(console, "Contraseña del admin (mínimo 8)");
            if (adminPassword.length >= 8) {
                break;
            }
            console.printf("Muy corta.%n");
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("DB_URL", url);
        values.put("DB_USERNAME", user);
        values.put("DB_PASSWORD", new String(password));
        values.put("JWT_SECRET", randomSecret());
        values.put("BOOTSTRAP_ADMIN_EMAIL", adminEmail);
        values.put("BOOTSTRAP_ADMIN_PASSWORD", new String(adminPassword));
        write(envFile, values);
        Arrays.fill(password, ' ');
        Arrays.fill(adminPassword, ' ');

        console.printf("%nListo: configuración guardada en %s.%n%n", envFile.toAbsolutePath());
    }

    private static String ask(Console console, String label, String defaultValue) {
        String answer = console.readLine("%s [%s]: ", label, defaultValue);
        return answer == null || answer.isBlank() ? defaultValue : answer.trim();
    }

    private static char[] askSecret(Console console, String label) {
        while (true) {
            char[] first = console.readPassword("%s: ", label);
            if (first == null || first.length == 0) {
                console.printf("No puede estar vacía.%n");
                continue;
            }
            char[] second = console.readPassword("Repite la contraseña: ");
            if (Arrays.equals(first, second)) {
                return first;
            }
            console.printf("No coinciden.%n");
        }
    }

    /** Returns null when the connection works or the MySQL driver is not on the classpath (running from source). */
    private static String testConnection(String url, String user, char[] password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return null;
        }
        try (Connection ignored = DriverManager.getConnection(url, user, new String(password))) {
            return null;
        } catch (SQLException e) {
            return e.getMessage();
        }
    }

    static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Writes KEY=VALUE lines that Spring reads as a .properties file, readable only by the owner. */
    static void write(Path file, Map<String, String> values) throws IOException {
        StringBuilder sb = new StringBuilder("# Generated by Minuteman first-run setup. Do not commit.\n");
        values.forEach((key, value) -> sb.append(key).append('=').append(escape(value)).append('\n'));
        Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Windows: no POSIX permissions; the file stays in the user's own folder.
        }
    }

    /** Properties-format escaping, so passwords with \, leading spaces or non-ASCII characters survive the round trip. */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == ' ' && i == 0) {
                sb.append("\\ ");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c > 0x7e) {
                // Spring reads .properties as ISO-8859-1; \\u escapes keep ñ, accents, etc. intact.
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
