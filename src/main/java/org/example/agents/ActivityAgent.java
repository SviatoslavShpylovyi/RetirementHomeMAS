package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jdk.jfr.Event;
import org.example.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Map;


public class ActivityAgent extends Agent {

    private List<Activity>  activities;
    private List<EventProposal> eventProposals;

    private static final String HEALTH_AGENT_NAME = "health-agent";
    private static final String HEALTH_CHECK_CONVERSATION = "health-check";

    private static final String RESOURCE_AGENT_NAME = "resource-agent";
    private  static final String RESOURCE_BOOKING_CONVERSATION = "resource-booking";

    @Override
    protected void setup(){
        System.out.println(getLocalName() + " started.");

        activities = new ArrayList<>();
        eventProposals = new ArrayList<>();

        addHealthAnswerReceiver();
        addResourceAnswerReceiver();

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                initializeActivities();

                printActivities();
                printEventProposals();

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
                + "|" + countActiveParticipants(proposal)
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

        String residentIds = String.join(",",proposal.getParticipantStatuses().keySet());

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

            if (result.equals("UNSAFE")) {
                updateParticipationStatus(proposalId, residentId, ParticipationStatus.DECLINED);

            } else {
                System.out.println("Resident " + residentId + " is safe for proposal " + proposalId);

            }

        }

        Optional<EventProposal> optionalProposal = findProposalById(proposalId);

        if(optionalProposal.isEmpty()){
            System.out.println("Proposal not found: " + proposalId);
        }else
        {
            EventProposal proposal = optionalProposal.get();

            if(countActiveParticipants(proposal) == 0){
                System.out.println("Proposal " + proposalId + " has no safe participants, so it will not be booked.");
                cancelProposal(proposalId);

            }else{
                askResourceAgentToBookProposal(proposal);
            }


            printEventProposals();
        }



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



}
