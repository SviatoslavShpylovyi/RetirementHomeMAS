package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jdk.jfr.Event;
import org.example.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;


public class ActivityAgent extends Agent {

    private List<Activity>  activities;
    private List<EventProposal> eventProposals;

    private static final String HEALTH_AGENT_NAME = "health-agent";
    private static final String HEALTH_CHECK_CONVERSATION = "health-check";

    private static final String RESOURCE_AGENT_NAME = "resource-agent";
    private  static final String RESOURCE_BOOKING_CONVERSATION = "resource-booking";

    private static final String SCENARIO_TO_ACTIVITY_CONVERSATION = "scenario-event-proposal";
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String RESIDENT_INVITATION_CONVERSATION = "resident-activity-invitation";

    private Map<String, Integer> pendingInvitationCounts;

    @Override
    protected void setup(){
        System.out.println(getLocalName() + " started.");

        activities = new ArrayList<>();
        eventProposals = new ArrayList<>();
        pendingInvitationCounts = new HashMap<>();
        addHealthAnswerReceiver();
        addResourceAnswerReceiver();
        addScenarioProposalReceiver();
        addResidentInvitationAnswerReceiver();
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                initializeActivities();
                initializeSampleEventProposals();

                printActivities();
                printEventProposals();

                for(EventProposal proposal : eventProposals)
                {
                    askHealthAgentToCheckProposal(proposal);
                }

            }
        });

    }

    // communication with ResourceAgent

    private void askResourceAgentToBookProposal(EventProposal proposal){
        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

        message.addReceiver(new AID(RESOURCE_AGENT_NAME,AID.ISLOCALNAME));
        message.setConversationId(RESOURCE_BOOKING_CONVERSATION);
        message.setContent(buildResourceBookingMessage(proposal));

        send(message);

        System.out.println(getLocalName() + " -> " + RESOURCE_AGENT_NAME + ": booking request for proposal " + proposal.getId());

    }

    private String buildResourceBookingMessage(EventProposal proposal){
        TimeSlot timeSlot = proposal.getTimeSlot();

        return proposal.getId()
                + "|" + proposal.getRoom().getId()
                + "|" + countConfirmedParticipants(proposal)
                + "|" + timeSlot.getStartTime()
                + "|" + timeSlot.getEndTime()
                + "|" + buildRequiredResourcesPart(proposal);
    }

    private String buildRequiredResourcesPart(EventProposal proposal){
        Map<String, Integer> requiredResources = proposal.getRequiredResources();

        if(requiredResources == null || requiredResources.isEmpty()){
            return "-";
        }

        StringBuilder builder = new StringBuilder();

        for(Map.Entry<String,Integer> entry: requiredResources.entrySet()){
            if(builder.length() > 0){
                builder.append(",");
            }

            builder.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return builder.toString();
    }

    private void addResourceAnswerReceiver(){
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESOURCE_BOOKING_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                );

                ACLMessage message = myAgent.receive(template);

                if(message==null){
                    block();
                }else{
                    handleResourceAnswer(message.getContent());
                }

            }
        });
    }

    private void handleResourceAnswer(String content){
        if (content == null || content.isBlank()) {
            return;
        }

        System.out.println(getLocalName() + " <- " + RESOURCE_AGENT_NAME + ": " + content);

        String[] parts = content.split("\\|");

        if (parts.length < 2) {
            System.out.println("Wrong resource answer format.");
            return;
        }

        String proposalId = parts[0];
        String result = parts[1];

        if(result.equals("ACCEPTED")){
            System.out.println("Proposal " + proposalId + " was booked successfully.");
        }else{
            String reason = "No reason given.";

            if(parts.length >= 3){
                reason = parts[2];
            }

            System.out.println("Proposal " + proposalId + " was rejected by ResourceAgent: " + reason);
            cancelProposal(proposalId);

        }

        printEventProposals();
    }

    // communication with HealthAgent
    private void askHealthAgentToCheckProposal(EventProposal proposal){
        ACLMessage message = new ACLMessage(ACLMessage.QUERY_IF);

        message.addReceiver(new AID(HEALTH_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(HEALTH_CHECK_CONVERSATION);
        message.setContent(buildHealthCheckMessage(proposal));

        send(message);

        System.out.println(getLocalName() + " -> " + HEALTH_AGENT_NAME + ": health check for proposal " + proposal.getId());
    }

    private String buildHealthCheckMessage(EventProposal proposal){
        Activity activity = proposal.getActivity();
        Room room = proposal.getRoom();

        String residentIds = proposal.getParticipantStatuses()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == ParticipationStatus.SUGGESTED)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));

        return proposal.getId() + "|" + activity.getId() + "|" + activity.getType() + "|" + activity.getRequiredMobilityLevel() + "|" + room.isAccessible() + "|" + residentIds;
    }

    private void addHealthAnswerReceiver(){

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(HEALTH_CHECK_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                );

                ACLMessage message = myAgent.receive(template);

                if(message == null){
                    block();
                }
                else{
                    handleHealthAnswer(message.getContent());
                }


            }
        });
    }

    private void handleHealthAnswer(String content){
        if(content == null || content.isBlank()){
            return;
        }
        System.out.println(getLocalName() + " <- " + HEALTH_AGENT_NAME + ": " + content);

        String[] parts = content.split("\\|");

        if(parts.length < 2){
            System.out.println("Wrong health answer format.");
            return;
        }

        String proposalId = parts[0];

        for(int i=1; i<parts.length; i++) {
            String[] residentResult = parts[i].split(":");

            if (residentResult.length != 2) {
                continue;
            }

            String residentId = residentResult[0];
            String result = residentResult[1];

            if("SAFE".equals(result)) {
                updateParticipationStatus(
                        proposalId,
                        residentId,
                        ParticipationStatus.HEALTH_APPROVED
                );
                System.out.println(
                        "Resident " + residentId
                                + " is safe for proposal " + proposalId
                );
            } else {
                updateParticipationStatus(
                        proposalId,
                        residentId,
                        ParticipationStatus.DECLINED
                );

                System.out.println(
                        "Resident " + residentId
                                + " is unsafe for proposal " + proposalId
                                + ". Invitation will not be sent."
                );
            }


        }

        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if(optionalProposal.isEmpty()){
            System.out.println("Proposal not found: " + proposalId);
            return;
        }
        EventProposal proposal = optionalProposal.get();

        if (countHealthApprovedParticipants(proposal) == 0) {
            System.out.println(
                    "Proposal " + proposalId
                            + " has no safe residents. Cancelling proposal."
            );

            cancelProposal(proposalId);
            return;
        }

        inviteHealthApprovedResidents(proposal);

        printEventProposals();

    }
    private int countHealthApprovedParticipants(EventProposal proposal) {
        int count = 0;

        for (ParticipationStatus status : proposal.getParticipantStatuses().values()) {
            if (status == ParticipationStatus.HEALTH_APPROVED) {
                count++;
            }
        }

        return count;
    }

    private int countActiveParticipants(EventProposal proposal){
        int count=0;

        for(ParticipationStatus status: proposal.getParticipantStatuses().values()){
            if(status!= ParticipationStatus.DECLINED){
                count++;
            }
        }
        return count;
    }




    private void initializeActivities(){


        // START: MOCK DATA - REMOVE LATER
        activities.add(new Activity(
                "A1",
                "Morning Music Session",
                ActivityType.MUSIC,
                10,
                MobilityLevel.LOW
        ));


        activities.add(new Activity(
                "A2",
                "Garden Walk",
                ActivityType.WALKING,
                6,
                MobilityLevel.MEDIUM
        ));

        activities.add(new Activity(
                "A3",
                "Board Games Afternoon",
                ActivityType.BOARD_GAMES,
                8,
                MobilityLevel.LOW
        ));

        activities.add(new Activity(
                "A4",
                "Art Workshop",
                ActivityType.ART,
                7,
                MobilityLevel.LOW
        ));

        activities.add(new Activity(
                "A5",
                "Movie Evening",
                ActivityType.MOVIE,
                12,
                MobilityLevel.LOW
        ));
        // END: MOCK DATA - REMOVE LATER
    }


    private void initializeSampleEventProposals(){
        Activity musicActivity = findActivityById("A1").orElseThrow();

        Room commonRoom = new Room(
                "ROOM1",
                "Common Room",
                15,
                true
        );

        TimeSlot musicSlot = new TimeSlot(
                LocalDateTime.of(2026, 5, 7, 10, 0),
                LocalDateTime.of(2026, 5, 7, 11, 0)
        );

        EventProposal musicProposal = new EventProposal(
                "E1",
                musicSlot,
                musicActivity,
                commonRoom
        );

        musicProposal.updateParticipationStatus("R1", ParticipationStatus.SUGGESTED);
        musicProposal.updateParticipationStatus("R2", ParticipationStatus.SUGGESTED);
        musicProposal.getRequiredResources().put("RES-SPEAKER", 1);

        eventProposals.add(musicProposal);
    }

    public void addActivity(Activity activity) {
        activities.add(activity);
    }

    public void addEventProposal(EventProposal proposal) {
        eventProposals.add(proposal);
    }

    public Optional<Activity> findActivityById(String activityId)
    {
        return activities.stream()
                .filter(activity -> activity.getId().equals(activityId))
                .findFirst();
    }

    public Optional<EventProposal> findProposalById(String proposalId) {
        return eventProposals.stream()
                .filter(proposal -> proposal.getId().equals(proposalId))
                .findFirst();
    }

    public boolean canAcceptMoreParticipants(String proposalId){
        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if(optionalProposal.isEmpty())
        {
            return false;
        }

        return optionalProposal.get().hasFreePlaces();

    }

    public void updateParticipationStatus(String proposalId, String residentId, ParticipationStatus status)
    {
        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if (optionalProposal.isEmpty()) {
            System.out.println("Proposal not found: " + proposalId);
            return;
        }

        EventProposal proposal = optionalProposal.get();

        if (status == ParticipationStatus.CONFIRMED && !proposal.hasFreePlaces()) {
            System.out.println("Cannot confirm resident " + residentId + ". Proposal is full.");
            return;
        }

        proposal.updateParticipationStatus(residentId, status);

        System.out.println(
                "Updated resident " + residentId +
                        " in proposal " + proposalId +
                        " to " + status
        );
    }

    public void cancelProposal(String proposalId) {
        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if (optionalProposal.isEmpty()) {
            System.out.println("Proposal not found: " + proposalId);
            return;
        }

        eventProposals.remove(optionalProposal.get());
        System.out.println("Cancelled proposal: " + proposalId);
    }

    private void printActivities() {
        System.out.println("\nActivities managed by " + getLocalName() + ":");

        for (Activity activity : activities) {
            System.out.println(" - " + activity);
        }
    }

    private void printEventProposals() {
        System.out.println("\nEvent proposals managed by " + getLocalName() + ":");

        for (EventProposal proposal : eventProposals) {
            System.out.println(" - " + proposal);
        }
    }
    // communication with ScenarioAgent
    private void addScenarioProposalReceiver() {
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(SCENARIO_TO_ACTIVITY_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleScenarioEventProposal(message.getContent());
            }
        });
    }
    private void handleScenarioEventProposal(String content) {
        if (content == null || content.isBlank()) {
            System.out.println("Received empty event proposal from ScenarioAgent.");
            return;
        }

        try {
            EventProposal proposal = parseEventProposal(content);
            Activity activity = proposal.getActivity();

            if (findActivityById(activity.getId()).isEmpty()) {
                addActivity(activity);
            }

            if (findProposalById(proposal.getId()).isPresent()) {
                System.out.println("Event proposal already exists: " + proposal.getId());
                return;
            }

            addEventProposal(proposal);

            System.out.println(getLocalName() + " <- scenario-agent: received event proposal");
            System.out.println(proposal);

            printActivities();
            printEventProposals();

            askHealthAgentToCheckProposal(proposal);

        } catch (Exception ex) {
            System.err.println("ActivityAgent failed to process event proposal from ScenarioAgent:");
            ex.printStackTrace();
        }
    }
    private EventProposal parseEventProposal(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        String proposalId = root.get("id").asText();

        JsonNode activityNode = root.get("activity");

        Activity activity = new Activity(
                activityNode.get("id").asText(),
                activityNode.get("name").asText(),
                ActivityType.valueOf(activityNode.get("type").asText()),
                activityNode.get("maxParticipants").asInt(),
                MobilityLevel.valueOf(activityNode.get("requiredMobilityLevel").asText())
        );

        JsonNode roomNode = root.get("room");

        Room room = new Room(
                roomNode.get("id").asText(),
                roomNode.get("name").asText(),
                roomNode.get("capacity").asInt(),
                roomNode.get("accessible").asBoolean()
        );

        JsonNode timeSlotNode = root.get("timeSlot");

        TimeSlot timeSlot = new TimeSlot(
                LocalDateTime.parse(timeSlotNode.get("startTime").asText()),
                LocalDateTime.parse(timeSlotNode.get("endTime").asText())
        );

        EventProposal proposal = new EventProposal(
                proposalId,
                timeSlot,
                activity,
                room
        );

        JsonNode participantStatusesNode = root.get("participantStatuses");

        if (participantStatusesNode != null && participantStatusesNode.isObject()) {
            participantStatusesNode.fields().forEachRemaining(entry -> {
                String residentId = entry.getKey();
                ParticipationStatus status = ParticipationStatus.valueOf(entry.getValue().asText());

                proposal.updateParticipationStatus(residentId, status);
            });
        }

        JsonNode requiredResourcesNode = root.get("requiredResources");

        if (requiredResourcesNode != null && requiredResourcesNode.isObject()) {
            requiredResourcesNode.fields().forEachRemaining(entry -> {
                String resourceId = entry.getKey();
                int quantity = entry.getValue().asInt();

                proposal.addRequiredResource(resourceId, quantity);
            });
        }

        return proposal;
    }
    // communication with ResidentAgent
    private void inviteHealthApprovedResidents(EventProposal proposal) {
        List<String> safeResidentIds = proposal.getParticipantStatuses()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == ParticipationStatus.HEALTH_APPROVED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (safeResidentIds.isEmpty()) {
            System.out.println(
                    "Proposal " + proposal.getId()
                            + " has no health-approved residents."
            );

            cancelProposal(proposal.getId());
            return;
        }

        pendingInvitationCounts.put(proposal.getId(), safeResidentIds.size());

        for (String residentId : safeResidentIds) {
            sendInvitationToResident(proposal, residentId);
            proposal.updateParticipationStatus(residentId, ParticipationStatus.INVITED);
        }
    }
    private void sendInvitationToResident(EventProposal proposal, String residentId) {
        String residentAgentName = residentId;

        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

        message.addReceiver(new AID(residentAgentName, AID.ISLOCALNAME));
        message.setConversationId(RESIDENT_INVITATION_CONVERSATION);
        message.setContent(buildResidentInvitationMessage(proposal, residentId));

        send(message);

        System.out.println(
                getLocalName() + " -> " + residentAgentName
                        + ": invitation for proposal " + proposal.getId()
        );
    }
    private String buildResidentInvitationMessage(EventProposal proposal, String residentId) {
        Activity activity = proposal.getActivity();
        TimeSlot timeSlot = proposal.getTimeSlot();

        return proposal.getId()
                + "|" + residentId
                + "|" + activity.getId()
                + "|" + activity.getName()
                + "|" + activity.getType()
                + "|" + activity.getMaxParticipants()
                + "|" + activity.getRequiredMobilityLevel()
                + "|" + timeSlot.getStartTime()
                + "|" + timeSlot.getEndTime();
    }
    private void addResidentInvitationAnswerReceiver() {
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESIDENT_INVITATION_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleResidentInvitationAnswer(message.getContent());
            }
        });
    }
    private void handleResidentInvitationAnswer(String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        System.out.println(getLocalName() + " <- resident: " + content);

        String[] parts = content.split("\\|");

        if (parts.length < 3) {
            System.out.println("Wrong resident invitation answer format.");
            return;
        }

        String proposalId = parts[0];
        String residentId = parts[1];
        String answer = parts[2];

        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if (optionalProposal.isEmpty()) {
            System.out.println("Proposal not found: " + proposalId);
            return;
        }

        EventProposal proposal = optionalProposal.get();

        if ("ACCEPTED".equals(answer)) {
            proposal.updateParticipationStatus(residentId, ParticipationStatus.CONFIRMED);

            System.out.println(
                    "Resident " + residentId
                            + " accepted proposal " + proposalId
            );
        } else {
            proposal.updateParticipationStatus(residentId, ParticipationStatus.DECLINED);

            String reason = "No reason given.";

            if (parts.length >= 4) {
                reason = parts[3];
            }

            System.out.println(
                    "Resident " + residentId
                            + " declined proposal " + proposalId
                            + ": " + reason
            );
        }

        decreasePendingInvitationCount(proposalId);
        continueAfterAllResidentAnswers(proposalId);
    }
    private void decreasePendingInvitationCount(String proposalId) {
        int currentCount = pendingInvitationCounts.getOrDefault(proposalId, 0);

        if (currentCount > 0) {
            pendingInvitationCounts.put(proposalId, currentCount - 1);
        }
    }
    private void continueAfterAllResidentAnswers(String proposalId) {
        int pendingCount = pendingInvitationCounts.getOrDefault(proposalId, 0);

        if (pendingCount > 0) {
            return;
        }

        pendingInvitationCounts.remove(proposalId);

        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if (optionalProposal.isEmpty()) {
            return;
        }

        EventProposal proposal = optionalProposal.get();

        if (countConfirmedParticipants(proposal) == 0) {
            System.out.println(
                    "Proposal " + proposalId
                            + " has no confirmed residents. Cancelling proposal."
            );

            cancelProposal(proposalId);
            return;
        }

        System.out.println(
                "All resident answers received for proposal "
                        + proposalId
                        + ". Starting resource booking."
        );

        askResourceAgentToBookProposal(proposal);
    }
    private int countConfirmedParticipants(EventProposal proposal) {
        int count = 0;

        for (ParticipationStatus status : proposal.getParticipantStatuses().values()) {
            if (status == ParticipationStatus.CONFIRMED) {
                count++;
            }
        }

        return count;
    }


}
