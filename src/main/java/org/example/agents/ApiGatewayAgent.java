package org.example.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.example.logging.FrontendLogStore;

public class ApiGatewayAgent extends Agent {
    private static final String CONVERSATION_ID = "activity-proposal-create";
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> ACTIVITY_TYPES = List.of(
            "MUSIC",
            "ART",
            "BOARD_GAMES",
            "WALKING",
            "READING",
            "FITNESS",
            "MOVIE",
            "SOCIAL_TEA",
            "KNITTING",
            "CROCHETING",
            "POKER",
            "BINGO"
    );

    private static final List<ResidentProfile> RESIDENTS = List.of(
            new ResidentProfile("R1", "Anna", true, "LOW", List.of("Avoid long walking"),
                    List.of("MUSIC", "SOCIAL_TEA", "BINGO")),
            new ResidentProfile("R2", "Jan", true, "MEDIUM", List.of(),
                    List.of("BOARD_GAMES", "POKER", "READING")),
            new ResidentProfile("R3", "Maria", false, "HIGH", List.of("Sensitive to noise"),
                    List.of("ART", "KNITTING", "MOVIE")),
            new ResidentProfile("R4", "Ewa", true, "LOW", List.of("Needs seated activities"),
                    List.of("ART", "READING", "KNITTING")),
            new ResidentProfile("R5", "Piotr", true, "HIGH", List.of(),
                    List.of("WALKING", "FITNESS", "BOARD_GAMES")),
            new ResidentProfile("R6", "Zofia", true, "MEDIUM", List.of("Avoid loud music"),
                    List.of("SOCIAL_TEA", "CROCHETING", "BINGO")),
            new ResidentProfile("R7", "Tomasz", false, "MEDIUM", List.of("Prefers small groups"),
                    List.of("POKER", "READING", "MOVIE"))
    );

    private final BlockingQueue<String> incomingActivityJson = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final Object simulationLock = new Object();
    private final Map<String, Integer> attendanceByResident = new LinkedHashMap<>();

    private HttpServer server;
    private Map<String, Object> currentSimulationPayload;
    private int scenarioSequence;
    private int proposedEvents;
    private int acceptedEvents;

    @Override
    protected void setup() {
        for (ResidentProfile resident : RESIDENTS) {
            attendanceByResident.put(resident.id(), 0);
        }

        synchronized (simulationLock) {
            currentSimulationPayload = createSimulationPayload(generateActivityForNextScenario());
        }

        int port = 8080;
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Integer) {
            port = (Integer) args[0];
        }

        try {
            startHttpServer(port);
            System.out.println(getLocalName() + " HTTP API started on http://localhost:" + port);
        } catch (IOException ex) {
            System.err.println("Cannot start API gateway:" + ex.getMessage());
            doDelete();
            return;
        }

        addBehaviour(new TickerBehaviour(this, 500) {
            @Override
            protected void onTick() {
                String json;
                while ((json = incomingActivityJson.poll()) != null) {
                    ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
                    msg.addReceiver(new AID("scenario-agent", AID.ISLOCALNAME));
                    msg.setConversationId(CONVERSATION_ID);
                    msg.setLanguage("JSON");
                    msg.setContent(json);
                    send(msg);
                }
            }
        });
    }

    private void startHttpServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/activity-proposals", this::handleActivityProposal);
        server.createContext("/api/simulation", this::handleSimulation);
        server.createContext("/api/simulation/new", this::handleNewSimulation);
        server.createContext("/api/simulation/step-log", this::handleSimulationStepLog);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private void handleActivityProposal(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            respond(exchange, 400, "{\"error\":\"Empty JSON body\"}");
            return;
        }

        incomingActivityJson.offer(body);
        parsePostedActivity(body).ifPresent(activity -> {
            synchronized (simulationLock) {
                currentSimulationPayload = createSimulationPayload(activity);
            }
        });

        respond(exchange, 202, "{\"status\":\"accepted\"}");
    }

    private void handleSimulation(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        synchronized (simulationLock) {
            respond(exchange, 200, mapper.writeValueAsString(currentSimulationPayload));
        }
    }

    private void handleNewSimulation(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        synchronized (simulationLock) {
            currentSimulationPayload = createSimulationPayload(generateActivityForNextScenario());
            respond(exchange, 200, mapper.writeValueAsString(currentSimulationPayload));
        }
    }

    private void handleSimulationStepLog(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            respond(exchange, 400, "{\"error\":\"Empty JSON body\"}");
            return;
        }

        try {
            JsonNode root = mapper.readTree(body);
            String scenarioId = textOrDefault(root.path("scenarioId"), "unknown-scenario");
            int stepNumber = root.path("stepNumber").asInt();
            String agent = textOrDefault(root.path("agent"), "Unknown Agent");
            String log = textOrDefault(root.path("log"), "No log content");

            System.out.println("[simulation-step " + scenarioId + " #" + stepNumber + "] " + agent + ": " + log);
            respond(exchange, 202, "{\"status\":\"logged\"}");
        } catch (Exception ex) {
            respond(exchange, 400, "{\"error\":\"Invalid step log JSON\"}");
        }
    }

    private ActivityDraft generateActivityForNextScenario() {
        int nextSequence = scenarioSequence + 1;

        return requestActivityFromLlm(nextSequence)
                .orElseGet(() -> fallbackActivity(nextSequence));
    }

    private Optional<ActivityDraft> requestActivityFromLlm(int nextSequence) {
        String llmAgentUrl = System.getenv().getOrDefault("LLM_AGENT_URL", "http://127.0.0.1:8000")
                .replaceAll("/+$", "");

        Map<String, Object> requestBody = jsonObject(
                "preferredEvents", preferredEventsFor(nextSequence),
                "preferredNumOfParticipants", List.of(4 + (nextSequence % 3), 10 + (nextSequence % 5)),
                "preferredMobility", List.of(preferredMobilityFor(nextSequence))
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(llmAgentUrl + "/generate-only"))
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                System.out.println("LLM activity generator returned " + response.statusCode()
                        + " with body: " + response.body()
                        + ". Using local fallback scenario.");
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode payload = root.path("payload");
            ActivityDraft draft = new ActivityDraft(
                    textOrDefault(payload.path("id"), "LLM-ACT-" + nextSequence),
                    textOrDefault(payload.path("name"), "Generated Activity"),
                    normalizedActivityType(textOrDefault(payload.path("type"), preferredEventsFor(nextSequence).get(0))),
                    Math.max(4, payload.path("maxParticipants").asInt(8)),
                    normalizedMobility(textOrDefault(payload.path("requiredMobilityLevel"), "LOW")),
                    textOrDefault(root.path("explanation"), "Generated by the LLM activity agent."),
                    "LLM Agent"
            );

            return Optional.of(draft);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getClass().getSimpleName() + ": " + ex.getMessage();
            System.out.println("LLM activity generator unavailable, using local fallback scenario: " + message);
            return Optional.empty();
        }
    }

    private Optional<ActivityDraft> parsePostedActivity(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode()) {
                return Optional.empty();
            }

            return Optional.of(new ActivityDraft(
                    textOrDefault(payload.path("id"), "POSTED-ACT-" + (scenarioSequence + 1)),
                    textOrDefault(payload.path("name"), "Posted Activity"),
                    normalizedActivityType(textOrDefault(payload.path("type"), "SOCIAL_TEA")),
                    Math.max(4, payload.path("maxParticipants").asInt(8)),
                    normalizedMobility(textOrDefault(payload.path("requiredMobilityLevel"), "LOW")),
                    textOrDefault(root.path("explanation"), "Posted by the LLM activity agent."),
                    "LLM Agent POST"
            ));
        } catch (Exception ex) {
            System.out.println("Could not parse posted activity for simulation view: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> createSimulationPayload(ActivityDraft activity) {
        scenarioSequence++;
        String proposalId = "EV-" + String.format(Locale.US, "%03d", scenarioSequence);
        LocalDateTime startTime = LocalDate.now()
                .plusDays(2L + scenarioSequence)
                .atTime(9 + (scenarioSequence % 6), 0);
        LocalDateTime endTime = startTime.plusMinutes(60 + ((scenarioSequence % 2) * 30L));

        RoomOption room = selectRoom(activity.type(), activity.requiredMobilityLevel(), scenarioSequence);
        List<ResourceNeed> requiredResources = resourcesForActivity(activity.type(), scenarioSequence);
        SocialResult socialResult = evaluateSocialSupport(activity);
        HealthResult healthResult = evaluateHealth(activity, room, socialResult.recommendedResidentIds());
        boolean hasSafeParticipants = !healthResult.safeResidentIds().isEmpty();
        boolean resourcesAvailable = hasSafeParticipants && areResourcesAvailable(requiredResources, scenarioSequence);
        boolean accepted = hasSafeParticipants && resourcesAvailable;

        proposedEvents++;
        if (accepted) {
            acceptedEvents++;
            for (String residentId : healthResult.safeResidentIds()) {
                attendanceByResident.computeIfPresent(residentId, (id, count) -> count + 1);
            }
        }

        Map<String, Object> payload = jsonObject(
                "kpis", buildKpis(),
                "scenario", jsonObject(
                        "id", proposalId,
                        "title", activity.name(),
                        "type", activity.type(),
                        "room", room.name(),
                        "time", DISPLAY_TIME.format(startTime) + "-" + DISPLAY_TIME.format(endTime).substring(11),
                        "requiredMobility", activity.requiredMobilityLevel(),
                        "maxParticipants", activity.maxParticipants(),
                        "resources", resourceObjects(requiredResources, "Required"),
                        "info", List.of(
                                "Source: " + activity.source(),
                                "Reason: " + activity.explanation(),
                                "Room capacity: " + room.capacity(),
                                "Accessible room: " + (room.accessible() ? "yes" : "no"),
                                "Proposed residents: " + residentNames(RESIDENTS)
                        )
                ),
                "residents", RESIDENTS.stream()
                        .map(resident -> jsonObject("id", resident.id(), "name", resident.name(), "status", "Waiting"))
                        .toList(),
                "steps", buildSimulationSteps(
                        proposalId,
                        activity,
                        room,
                        startTime,
                        endTime,
                        requiredResources,
                        socialResult,
                        healthResult,
                        resourcesAvailable,
                        accepted
                )
        );

        System.out.println("[simulation] Generated " + proposalId + " - " + activity.name()
                + " (" + activity.type() + "), outcome: " + (accepted ? "accepted" : "rejected"));

        return payload;
    }

    private List<Map<String, Object>> buildSimulationSteps(
            String proposalId,
            ActivityDraft activity,
            RoomOption room,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<ResourceNeed> requiredResources,
            SocialResult socialResult,
            HealthResult healthResult,
            boolean resourcesAvailable,
            boolean accepted
    ) {
        List<Map<String, Object>> steps = new ArrayList<>();

        steps.add(jsonObject(
                "id", proposalId + "-scenario-received",
                "agent", "Scenario Agent",
                "kind", "message",
                "from", activity.source(),
                "to", "Scenario Agent",
                "message", "Received generated activity proposal and parsed " + activity.name() + ".",
                "log", "ScenarioAgent received " + activity.source() + " proposal " + activity.id()
                        + ": " + activity.name()
        ));

        steps.add(jsonObject(
                "id", proposalId + "-scenario-social-request",
                "agent", "Scenario Agent",
                "kind", "message",
                "from", "Scenario Agent",
                "to", "Social Support Agent",
                "message", "Ask for resident recommendations for " + activity.name() + ".",
                "log", "scenario-agent -> social-support-agent: recommendation request for " + proposalId
        ));

        steps.add(jsonObject(
                "id", proposalId + "-social-support-decision",
                "agent", "Social Support Agent",
                "kind", "decision",
                "from", "Social Support Agent",
                "to", "Scenario Agent",
                "message", "Recommendation decision sent back to Scenario Agent.",
                "log", "social-support-agent -> scenario-agent: "
                        + residentNamesById(socialResult.recommendedResidentIds()) + " recommended for " + proposalId,
                "decisions", socialResult.decisions(),
                "residentStatuses", socialResult.residentStatuses()
        ));

        steps.add(jsonObject(
                "id", proposalId + "-activity-health-request",
                "agent", "Activity Agent",
                "kind", "message",
                "from", "Activity Agent",
                "to", "Health Agent",
                "message", "Health check for " + proposalId + " with recommended residents.",
                "log", "activity-agent -> health-agent: health check for " + proposalId
        ));

        steps.add(jsonObject(
                "id", proposalId + "-health-decision",
                "agent", "Health Agent",
                "kind", "decision",
                "from", "Health Agent",
                "to", "Activity Agent",
                "message", "Health answer for " + proposalId + ".",
                "log", "health-agent -> activity-agent: " + proposalId + healthResult.logSuffix(),
                "decisions", healthResult.decisions(),
                "residentStatuses", healthResult.residentStatuses()
        ));

        if (healthResult.safeResidentIds().isEmpty()) {
            steps.add(jsonObject(
                    "id", proposalId + "-activity-cancelled-health",
                    "agent", "Activity Agent",
                    "kind", "decision",
                    "from", "Activity Agent",
                    "to", "Scenario Agent",
                    "message", proposalId + " cancelled because no recommended residents passed health checks.",
                    "log", "Proposal " + proposalId + " has no safe participants, so it will not be booked.",
                    "decisions", List.of(
                            activity.name() + " rejected before resource booking.",
                            "No resident was scheduled for this event."
                    ),
                    "residentStatuses", finalResidentStatuses(healthResult.safeResidentIds(), false)
            ));
            return steps;
        }

        steps.add(jsonObject(
                "id", proposalId + "-activity-resource-request",
                "agent", "Activity Agent",
                "kind", "message",
                "from", "Activity Agent",
                "to", "Resource Agent",
                "message", "Booking request for " + room.name() + " and required resources.",
                "log", "activity-agent -> resource-agent: booking request for " + proposalId,
                "decisions", List.of(
                        "Active participants after health check: " + healthResult.safeResidentIds().size() + ".",
                        "Resource booking requested for " + DISPLAY_TIME.format(startTime)
                                + "-" + DISPLAY_TIME.format(endTime).substring(11) + "."
                )
        ));

        steps.add(jsonObject(
                "id", proposalId + "-resource-decision",
                "agent", "Resource Agent",
                "kind", "decision",
                "from", "Resource Agent",
                "to", "Activity Agent",
                "message", resourcesAvailable
                        ? proposalId + " accepted and resources booked."
                        : proposalId + " rejected because a required resource is unavailable.",
                "log", "resource-agent -> activity-agent: " + proposalId
                        + (resourcesAvailable ? "|ACCEPTED" : "|REJECTED|Resource unavailable"),
                "resources", bookedResourceObjects(room, requiredResources, resourcesAvailable),
                "decisions", resourceDecisions(room, requiredResources, healthResult.safeResidentIds().size(), resourcesAvailable)
        ));

        steps.add(jsonObject(
                "id", proposalId + "-activity-final",
                "agent", "Activity Agent",
                "kind", "decision",
                "from", "Activity Agent",
                "to", "Scenario Agent",
                "message", accepted
                        ? proposalId + " accepted after health and resource checks."
                        : proposalId + " rejected after resource checks.",
                "log", accepted
                        ? "Proposal " + proposalId + " was booked successfully."
                        : "Proposal " + proposalId + " was rejected by ResourceAgent.",
                "decisions", accepted
                        ? List.of(
                        activity.name() + " accepted.",
                        residentNamesById(healthResult.safeResidentIds()) + " scheduled.",
                        "KPI counters updated for an accepted event."
                )
                        : List.of(
                        activity.name() + " rejected.",
                        "No attendance was counted for this event.",
                        "KPI proposed-event counter updated only."
                ),
                "residentStatuses", finalResidentStatuses(healthResult.safeResidentIds(), accepted)
        ));

        return steps;
    }

    private List<Map<String, Object>> buildKpis() {
        int totalAttendance = attendanceByResident.values().stream().mapToInt(Integer::intValue).sum();
        double averageAttendance = (double) totalAttendance / RESIDENTS.size();

        return List.of(
                jsonObject("label", "number of events accepted", "value", String.valueOf(acceptedEvents)),
                jsonObject("label", "number of proposed events", "value", String.valueOf(proposedEvents)),
                jsonObject("label", "average number of attended events per resident",
                        "value", String.format(Locale.US, "%.1f", averageAttendance))
        );
    }

    private SocialResult evaluateSocialSupport(ActivityDraft activity) {
        Map<String, Object> statuses = new LinkedHashMap<>();
        List<String> recommendedIds = new ArrayList<>();
        List<String> decisions = new ArrayList<>();

        Map<ResidentProfile, Integer> scores = new LinkedHashMap<>();
        for (ResidentProfile resident : RESIDENTS) {
            int score = socialScore(resident, activity);
            scores.put(resident, score);
            if (score >= 45) {
                recommendedIds.add(resident.id());
            }
        }

        if (recommendedIds.isEmpty()) {
            scores.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .ifPresent(resident -> recommendedIds.add(resident.id()));
        }

        for (ResidentProfile resident : RESIDENTS) {
            boolean recommended = recommendedIds.contains(resident.id());
            statuses.put(resident.id(), recommended ? "Recommended" : "Not recommended");
            decisions.add(resident.name() + (recommended ? " recommended: " : " not recommended: ")
                    + socialReason(resident, activity, scores.get(resident)));
        }

        return new SocialResult(recommendedIds, decisions, statuses);
    }

    private HealthResult evaluateHealth(ActivityDraft activity, RoomOption room, List<String> recommendedResidentIds) {
        Map<String, Object> statuses = new LinkedHashMap<>();
        List<String> safeIds = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        StringBuilder logSuffix = new StringBuilder();

        for (ResidentProfile resident : RESIDENTS) {
            if (!recommendedResidentIds.contains(resident.id())) {
                statuses.put(resident.id(), "Not recommended");
                continue;
            }

            List<String> problems = healthProblems(resident, activity, room);
            boolean safe = problems.isEmpty();
            statuses.put(resident.id(), safe ? "Safe" : "Declined");
            logSuffix.append("|").append(resident.id()).append(safe ? ":SAFE" : ":UNSAFE");

            if (safe) {
                safeIds.add(resident.id());
                decisions.add(resident.name() + " safe: mobility and room checks passed.");
            } else {
                decisions.add(resident.name() + " unsafe: " + String.join(" ", problems));
            }
        }

        return new HealthResult(safeIds, decisions, statuses, logSuffix.toString());
    }

    private int socialScore(ResidentProfile resident, ActivityDraft activity) {
        int score = 0;
        if (resident.willingToParticipate()) {
            score += 30;
        } else {
            score -= 10;
        }

        if (resident.preferences().contains(activity.type())) {
            score += 45;
        }

        if (attendanceByResident.getOrDefault(resident.id(), 0) == 0) {
            score += 15;
        }

        if (isNoisyActivity(activity.type()) && hasNoiseLimitation(resident)) {
            score -= 25;
        }

        return score;
    }

    private String socialReason(ResidentProfile resident, ActivityDraft activity, int score) {
        if (resident.preferences().contains(activity.type()) && resident.willingToParticipate()) {
            return "preference match and willingness score " + score + ".";
        }

        if (resident.preferences().contains(activity.type())) {
            return "preference match, but willingness is low; score " + score + ".";
        }

        if (attendanceByResident.getOrDefault(resident.id(), 0) == 0) {
            return "low recent attendance gives a support boost; score " + score + ".";
        }

        return "activity is not a strong preference match; score " + score + ".";
    }

    private List<String> healthProblems(ResidentProfile resident, ActivityDraft activity, RoomOption room) {
        List<String> problems = new ArrayList<>();

        if (mobilityScore(resident.mobilityLevel()) < mobilityScore(activity.requiredMobilityLevel())) {
            problems.add(resident.name() + " has " + resident.mobilityLevel()
                    + " mobility but the activity requires " + activity.requiredMobilityLevel() + ".");
        }

        if ("LOW".equals(resident.mobilityLevel()) && !room.accessible()) {
            problems.add(room.name() + " is not accessible for LOW mobility.");
        }

        if (isNoisyActivity(activity.type()) && hasNoiseLimitation(resident)) {
            problems.add("Sensitive to noise for a noisy activity.");
        }

        if ("WALKING".equals(activity.type()) && resident.limitations().stream()
                .anyMatch(limitation -> limitation.toLowerCase(Locale.US).contains("walking"))) {
            problems.add("Walking limitation conflicts with the activity.");
        }

        return problems;
    }

    private boolean areResourcesAvailable(List<ResourceNeed> requiredResources, int sequence) {
        return requiredResources.isEmpty() || sequence % 5 != 0;
    }

    private RoomOption selectRoom(String activityType, String requiredMobility, int sequence) {
        if ("WALKING".equals(activityType)) {
            return new RoomOption("ROOM2", "Garden Area", 10, false);
        }

        if ("FITNESS".equals(activityType)) {
            return new RoomOption("ROOM3", "Therapy Room", 6, true);
        }

        if ("SOCIAL_TEA".equals(activityType) || sequence % 4 == 0) {
            return new RoomOption("ROOM4", "Dining Hall", 25, true);
        }

        if ("HIGH".equals(requiredMobility)) {
            return new RoomOption("ROOM3", "Therapy Room", 6, true);
        }

        return new RoomOption("ROOM1", "Common Room", 15, true);
    }

    private List<ResourceNeed> resourcesForActivity(String activityType, int sequence) {
        return switch (activityType) {
            case "MUSIC" -> List.of(new ResourceNeed("RES-SPEAKER", "Portable Speaker", 1));
            case "BOARD_GAMES" -> List.of(new ResourceNeed("RES-BOARD-GAME", "Board Game Set", 2));
            case "POKER" -> List.of(new ResourceNeed("RES-POKER-CARDS", "Poker Card Deck", 1));
            case "BINGO" -> List.of(new ResourceNeed("RES-BINGO-SET", "Bingo Set", 1));
            case "FITNESS" -> List.of(new ResourceNeed("RES-YOGA-MAT", "Yoga Mat", Math.min(8, 4 + sequence % 4)));
            case "KNITTING", "CROCHETING" -> List.of(new ResourceNeed("RES-KNITTING-SET", "Knitting Set", 4));
            case "MOVIE" -> List.of(
                    new ResourceNeed("RES-SPEAKER", "Portable Speaker", 1),
                    new ResourceNeed("RES-PROJECTOR", "Projector", 1)
            );
            case "ART" -> List.of(new ResourceNeed("RES-ART-KIT", "Art Kit", 6));
            case "SOCIAL_TEA" -> List.of(new ResourceNeed("RES-TEA-TROLLEY", "Tea Trolley", 1));
            default -> List.of();
        };
    }

    private List<Map<String, Object>> resourceObjects(List<ResourceNeed> requiredResources, String status) {
        return requiredResources.stream()
                .map(resource -> jsonObject(
                        "id", resource.id(),
                        "name", resource.name(),
                        "quantity", resource.quantity(),
                        "status", status
                ))
                .toList();
    }

    private List<Map<String, Object>> bookedResourceObjects(
            RoomOption room,
            List<ResourceNeed> requiredResources,
            boolean resourcesAvailable
    ) {
        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(jsonObject(
                "id", room.id(),
                "name", room.name(),
                "quantity", 1,
                "status", resourcesAvailable ? "Booked" : "Rejected"
        ));

        for (ResourceNeed resource : requiredResources) {
            resources.add(jsonObject(
                    "id", resource.id(),
                    "name", resource.name(),
                    "quantity", resource.quantity(),
                    "status", resourcesAvailable ? "Booked" : "Unavailable"
            ));
        }

        return resources;
    }

    private List<String> resourceDecisions(
            RoomOption room,
            List<ResourceNeed> requiredResources,
            int activeParticipants,
            boolean resourcesAvailable
    ) {
        List<String> decisions = new ArrayList<>();
        decisions.add(room.name() + " capacity " + room.capacity()
                + " checked for " + activeParticipants + " active participant(s).");

        if (requiredResources.isEmpty()) {
            decisions.add("No shared equipment was required for this activity.");
        } else if (resourcesAvailable) {
            decisions.add("Required resources are available and marked as used.");
        } else {
            decisions.add("At least one required resource is unavailable in this time slot.");
        }

        return decisions;
    }

    private Map<String, Object> finalResidentStatuses(List<String> safeResidentIds, boolean accepted) {
        Map<String, Object> statuses = new LinkedHashMap<>();

        for (ResidentProfile resident : RESIDENTS) {
            if (accepted && safeResidentIds.contains(resident.id())) {
                statuses.put(resident.id(), "Scheduled");
            } else if (safeResidentIds.contains(resident.id())) {
                statuses.put(resident.id(), "Cancelled");
            } else {
                statuses.put(resident.id(), "Declined");
            }
        }

        return statuses;
    }

    private List<String> preferredEventsFor(int sequence) {
        int first = Math.floorMod(sequence - 1, ACTIVITY_TYPES.size());
        int second = Math.floorMod(first + 4, ACTIVITY_TYPES.size());
        return List.of(ACTIVITY_TYPES.get(first), ACTIVITY_TYPES.get(second));
    }

    private String preferredMobilityFor(int sequence) {
        return switch (sequence % 3) {
            case 1 -> "LOW";
            case 2 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private ActivityDraft fallbackActivity(int sequence) {
        List<ActivityDraft> fallbacks = List.of(
                new ActivityDraft("LOCAL-ACT-001", "Bingo and Tea Social", "BINGO", 10, "LOW",
                        "Generated locally because the LLM service was unavailable.", "Local Generator"),
                new ActivityDraft("LOCAL-ACT-002", "Guided Garden Walk", "WALKING", 6, "MEDIUM",
                        "Generated locally because the LLM service was unavailable.", "Local Generator"),
                new ActivityDraft("LOCAL-ACT-003", "Watercolor Art Workshop", "ART", 8, "LOW",
                        "Generated locally because the LLM service was unavailable.", "Local Generator"),
                new ActivityDraft("LOCAL-ACT-004", "Reading Circle", "READING", 7, "LOW",
                        "Generated locally because the LLM service was unavailable.", "Local Generator"),
                new ActivityDraft("LOCAL-ACT-005", "Gentle Fitness Class", "FITNESS", 6, "MEDIUM",
                        "Generated locally because the LLM service was unavailable.", "Local Generator"),
                new ActivityDraft("LOCAL-ACT-006", "Movie Afternoon", "MOVIE", 12, "LOW",
                        "Generated locally because the LLM service was unavailable.", "Local Generator")
        );

        ActivityDraft selected = fallbacks.get(Math.floorMod(sequence - 1, fallbacks.size()));
        return new ActivityDraft(
                "LOCAL-ACT-" + String.format(Locale.US, "%03d", sequence),
                selected.name(),
                selected.type(),
                selected.maxParticipants(),
                selected.requiredMobilityLevel(),
                selected.explanation(),
                selected.source()
        );
    }

    private int mobilityScore(String mobilityLevel) {
        return switch (mobilityLevel) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 0;
        };
    }

    private boolean isNoisyActivity(String activityType) {
        return "MUSIC".equals(activityType) || "MOVIE".equals(activityType) || "BINGO".equals(activityType);
    }

    private boolean hasNoiseLimitation(ResidentProfile resident) {
        return resident.limitations().stream()
                .map(limitation -> limitation.toLowerCase(Locale.US))
                .anyMatch(limitation -> limitation.contains("noise") || limitation.contains("loud"));
    }

    private String limitationsText(ResidentProfile resident) {
        return resident.limitations().isEmpty() ? "no limitations" : String.join("; ", resident.limitations());
    }

    private String residentNames(List<ResidentProfile> residents) {
        return residents.stream()
                .map(ResidentProfile::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private String residentNamesById(List<String> residentIds) {
        if (residentIds.isEmpty()) {
            return "No residents";
        }

        return residentIds.stream()
                .map(id -> RESIDENTS.stream()
                        .filter(resident -> resident.id().equals(id))
                        .findFirst()
                        .map(ResidentProfile::name)
                        .orElse(id))
                .reduce((left, right) -> left + ", " + right)
                .orElse("No residents");
    }

    private String normalizedActivityType(String value) {
        String normalized = value.toUpperCase(Locale.US);
        return ACTIVITY_TYPES.contains(normalized) ? normalized : "SOCIAL_TEA";
    }

    private String normalizedMobility(String value) {
        String normalized = value.toUpperCase(Locale.US);
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH" -> normalized;
            default -> "LOW";
        };
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }

        String text = node.asText();
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    private Map<String, Object> jsonObject(Object... pairs) {
        Map<String, Object> object = new LinkedHashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            object.put((String) pairs[i], pairs[i + 1]);
        }

        return object;
    }

    private boolean handlePreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());

        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }

        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        addCorsHeaders(headers);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void takeDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private record ActivityDraft(
            String id,
            String name,
            String type,
            int maxParticipants,
            String requiredMobilityLevel,
            String explanation,
            String source
    ) {
    }

    private record ResidentProfile(
            String id,
            String name,
            boolean willingToParticipate,
            String mobilityLevel,
            List<String> limitations,
            List<String> preferences
    ) {
    }

    private record RoomOption(String id, String name, int capacity, boolean accessible) {
    }

    private record ResourceNeed(String id, String name, int quantity) {
    }

    private record SocialResult(
            List<String> recommendedResidentIds,
            List<String> decisions,
            Map<String, Object> residentStatuses
    ) {
    }

    private record HealthResult(
            List<String> safeResidentIds,
            List<String> decisions,
            Map<String, Object> residentStatuses,
            String logSuffix
    ) {
    }
}
