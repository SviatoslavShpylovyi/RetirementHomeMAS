package org.example.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

import java.util.Map;

public final class AgentLogSender {

    public static final String SOCIAL_SUPPORT_AGENT_NAME = "social-support-agent";
    public static final String FRONTEND_LOG_CONVERSATION = "frontend-log-event";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentLogSender() {
    }

    public static void info(
            Agent sender,
            String action,
            String message
    ) {
        send(sender, "INFO", action, message, Map.of());
    }

    public static void info(
            Agent sender,
            String action,
            String message,
            Map<String, Object> details
    ) {
        send(sender, "INFO", action, message, details);
    }

    public static void warn(
            Agent sender,
            String action,
            String message
    ) {
        send(sender, "WARN", action, message, Map.of());
    }

    public static void warn(
            Agent sender,
            String action,
            String message,
            Map<String, Object> details
    ) {
        send(sender, "WARN", action, message, details);
    }

    public static void error(
            Agent sender,
            String action,
            String message
    ) {
        send(sender, "ERROR", action, message, Map.of());
    }

    public static void error(
            Agent sender,
            String action,
            String message,
            Exception exception
    ) {
        send(
                sender,
                "ERROR",
                action,
                message,
                Map.of(
                        "exceptionType", exception.getClass().getSimpleName(),
                        "exceptionMessage", exception.getMessage() == null ? "" : exception.getMessage()
                )
        );
    }

    public static void error(
            Agent sender,
            String action,
            String message,
            Map<String, Object> details
    ) {
        send(sender, "ERROR", action, message, details);
    }

    private static void send(
            Agent sender,
            String level,
            String action,
            String message,
            Map<String, Object> details
    ) {
        if (sender == null) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "level", normalize(level),
                    "source", sender.getLocalName(),
                    "action", normalize(action),
                    "message", message == null ? "" : message,
                    "details", details == null ? Map.of() : details
            );

            ACLMessage logMessage = new ACLMessage(ACLMessage.INFORM);
            logMessage.addReceiver(new AID(SOCIAL_SUPPORT_AGENT_NAME, AID.ISLOCALNAME));
            logMessage.setConversationId(FRONTEND_LOG_CONVERSATION);
            logMessage.setContent(OBJECT_MAPPER.writeValueAsString(payload));

            sender.send(logMessage);

        } catch (JsonProcessingException exception) {
            System.err.println(
                    "[AgentLogSender] Failed to serialize log event from "
                            + sender.getLocalName()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        return value.trim();
    }
}