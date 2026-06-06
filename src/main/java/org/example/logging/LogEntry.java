package org.example.logging;
import java.util.Collections;
import java.util.Map;
public class LogEntry {
    private final long id;
    private final String timestamp;
    private final String level;
    private final String source;
    private final String action;
    private final String message;
    private final Map<String, Object> details;

    public LogEntry(
            long id,
            String timestamp,
            String level,
            String source,
            String action,
            String message,
            Map<String, Object> details
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.source = source;
        this.action = action;
        this.message = message;
        this.details = details == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(details);
    }
    public long getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

}
