package org.example.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory storage for frontend-readable logs.
 *
 * Important:
 * This is not a database.
 * Logs are stored only while the application is running.
 * If the application restarts, the logs disappear.
 */
public final class FrontendLogStore {

    private static final int MAX_LOGS = 1000;

    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    private static final List<LogEntry> ENTRIES = new ArrayList<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FrontendLogStore() {
    }

    public static LogEntry info(
            String source,
            String action,
            String message
    ) {
        return log("INFO", source, action, message, Map.of());
    }

    public static LogEntry info(
            String source,
            String action,
            String message,
            Map<String, Object> details
    ) {
        return log("INFO", source, action, message, details);
    }

    public static LogEntry warn(
            String source,
            String action,
            String message
    ) {
        return log("WARN", source, action, message, Map.of());
    }

    public static LogEntry warn(
            String source,
            String action,
            String message,
            Map<String, Object> details
    ) {
        return log("WARN", source, action, message, details);
    }

    public static LogEntry error(
            String source,
            String action,
            String message
    ) {
        return log("ERROR", source, action, message, Map.of());
    }

    public static LogEntry error(
            String source,
            String action,
            String message,
            Exception exception
    ) {
        return log(
                "ERROR",
                source,
                action,
                message,
                Map.of(
                        "exceptionType", exception.getClass().getSimpleName(),
                        "exceptionMessage", exception.getMessage() == null ? "" : exception.getMessage()
                )
        );
    }

    public static synchronized LogEntry log(
            String level,
            String source,
            String action,
            String message,
            Map<String, Object> details
    ) {
        LogEntry entry = new LogEntry(
                NEXT_ID.getAndIncrement(),
                Instant.now().toString(),
                normalize(level),
                normalize(source),
                normalize(action),
                message == null ? "" : message,
                details == null ? Map.of() : details
        );

        ENTRIES.add(entry);
        trimOldEntries();

        System.out.println(formatForConsole(entry));

        return entry;
    }

    public static synchronized List<LogEntry> getLogsAfter(long afterId, int limit) {
        int safeLimit = limit <= 0 ? 200 : Math.min(limit, 1000);

        return ENTRIES.stream()
                .filter(entry -> entry.getId() > afterId)
                .sorted(Comparator.comparingLong(LogEntry::getId))
                .limit(safeLimit)
                .toList();
    }

    public static synchronized String toJson(long afterId, int limit) {
        try {
            return OBJECT_MAPPER.writeValueAsString(getLogsAfter(afterId, limit));
        } catch (JsonProcessingException e) {
            return """
                    [{"level":"ERROR","source":"FrontendLogStore","action":"LOG_SERIALIZATION_FAILED","message":"Could not serialize logs","details":{}}]
                    """;
        }
    }

    public static synchronized String toJson() {
        return toJson(0, 200);
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    private static void trimOldEntries() {
        while (ENTRIES.size() > MAX_LOGS) {
            ENTRIES.remove(0);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        return value.trim();
    }

    private static String formatForConsole(LogEntry entry) {
        return "[FRONTEND-LOG] "
                + entry.getTimestamp()
                + " | "
                + entry.getLevel()
                + " | "
                + entry.getSource()
                + " | "
                + entry.getAction()
                + " | "
                + entry.getMessage();
    }
}