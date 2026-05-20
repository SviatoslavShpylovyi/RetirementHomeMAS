package org.example.agents;

import jade.core.Agent;
import org.example.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;


public class HealthAgent extends Agent {

    private static final String HEALTH_CHECK_CONVERSATION = "health-check";
    private static final String RESIDENT_HEALTH_CONVERSATION = "resident-health-info";

    private List<Resident> residents;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        residents = new ArrayList<>();

        addResidentHealthInfoReceiver();
        addHealthQuestionReceiver();

    }

    //communication with ResidentAgent
    private void addResidentHealthInfoReceiver(){
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESIDENT_HEALTH_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                } else {
                    handleResidentHealthInfo(message.getContent());
                }
            }
        });
    }

    private void handleResidentHealthInfo(String content){
        if (content == null || content.isBlank()) {
            return;
        }

        System.out.println(getLocalName() + " received resident health info: " + content);

        String[] parts = content.split("\\|", -1);

        if (parts.length < 2) {
            System.out.println("Wrong resident health message format.");
            return;
        }

        String action = parts[0];

        if (action.equals("ADD")) {
            addResidentHealthInfo(parts);
        } else if (action.equals("UPDATE")) {
            updateResidentHealthInfo(parts);
        } else if (action.equals("REMOVE")) {
            removeResidentHealthInfo(parts[1]);
        } else {
            System.out.println("Unknown resident health action: " + action);
        }

    }

    private void addResidentHealthInfo(String[] parts){

        if (parts.length != 6) {
            System.out.println("Wrong ADD resident health message format.");
            return;
        }

        Resident newResidentData = buildResidentFromMessage(parts);

        if (findResidentById(newResidentData.getId()) != null) {
            System.out.println(getLocalName() + ": resident " + newResidentData.getId()   + " already exists, ADD ignored.");

        }else{
            residents.add(newResidentData);

            System.out.println(getLocalName() + ": added health info for resident "  + newResidentData.getId());

            printKnownResidents();
        }
    }

    private void updateResidentHealthInfo(String[] parts){
        if (parts.length != 6) {
            System.out.println("Wrong UPDATE resident health message format.");
            return;
        }

        Resident updatedResidentData = buildResidentFromMessage(parts);

        for (int i = 0; i < residents.size(); i++) {
            if (residents.get(i).getId().equals(updatedResidentData.getId())) {
                residents.set(i, updatedResidentData);

                System.out.println(getLocalName() + ": updated health info for resident "
                        + updatedResidentData.getId());

                printKnownResidents();
                return;
            }
        }

        System.out.println(getLocalName() + ": cannot update resident " + updatedResidentData.getId() + " because no health info exists yet.");
    }

    private Resident buildResidentFromMessage(String[] parts){
        String residentId = parts[1];
        String name = parts[2];
        boolean willingToParticipate = Boolean.parseBoolean(parts[3]);
        MobilityLevel mobilityLevel = MobilityLevel.valueOf(parts[4]);
        List<String> limitations = parseLimitations(parts[5]);

        return new Resident(
                willingToParticipate,
                new HealthProfile(mobilityLevel, limitations),
                new ArrayList<>(),
                residentId,
                name
        );
    }

    private void removeResidentHealthInfo(String residentId){
        boolean removed = residents.removeIf(resident -> resident.getId().equals(residentId));

        if (removed) {
            System.out.println(getLocalName() + ": removed health info for resident " + residentId);
        } else {
            System.out.println(getLocalName() + ": no health info found for resident " + residentId);
        }

        printKnownResidents();
    }

    private List<String> parseLimitations(String limitationsPart){
        List<String> limitations = new ArrayList<>();

        if (limitationsPart == null || limitationsPart.isBlank() || limitationsPart.equals("-")) {
            return limitations;
        }

        String[] parts = limitationsPart.split(";");

        for (String part : parts) {
            if (!part.isBlank()) {
                limitations.add(part);
            }
        }

        return limitations;
    }

    private void printKnownResidents(){
        System.out.println("\nHealth data known by " + getLocalName() + ":");

        if (residents.isEmpty()) {
            System.out.println(" - No residents.");
            return;
        }

        for (Resident resident : residents) {
            System.out.println(" - " + resident.getId()
                    + ", mobility: " + resident.getHealthProfile().getMobilityLevel()
                    + ", limitations: " + resident.getHealthProfile().getLimitations());
        }
    }

    // communication with ActivityAgent
    private void addHealthQuestionReceiver(){
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(HEALTH_CHECK_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF)
                );

                ACLMessage message = myAgent.receive(template);

                if(message == null){
                    block();

                }
                else{
                    handleHealthQuestion(message);

                }
            }
        });
    }

    private void handleHealthQuestion(ACLMessage message){
        String answerContent = checkProposalHealth(message.getContent());

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(HEALTH_CHECK_CONVERSATION);
        reply.setContent(answerContent);

        send(reply);

        System.out.println( getLocalName() + " -> " + message.getSender().getLocalName()   + ": " + answerContent);
    }

    private String checkProposalHealth(String content){
        if(content == null || content.isBlank()){
            return "UNKNOWN|ERROR";
        }

        String[] parts = content.split("\\|",-1);

        if(parts.length != 6){
            return "UNKNOWN|ERROR";
        }

        String proposalId = parts[0];
        String activityId = parts[1];
        ActivityType activityType = ActivityType.valueOf(parts[2]);
        MobilityLevel requiredMobility = MobilityLevel.valueOf(parts[3]);
        boolean roomAccessible = Boolean.parseBoolean(parts[4]);
        String[] residentIds = parts[5].split(",");

        Activity activity = new Activity(activityId,activityId,activityType,0,requiredMobility);

        Room room = new Room("ROOM","Room",0,roomAccessible);
        StringBuilder answer = new StringBuilder(proposalId);

        for(String residentId: residentIds){
            Resident resident = findResidentById(residentId);

            if(resident==null){
                answer.append("|").append(residentId).append(":UNSAFE");
                System.out.println("No health data found for resident " + residentId);
                continue;
            }

            HealthCheckResult result = validateResidentForActivity(resident,activity);
            result.merge(validateResidentForRoom(resident,room));

            System.out.println("Health check for " + residentId + ": " + result);

            if(result.isSafe()){
                answer.append("|").append(residentId).append(":SAFE");
            }else{
                answer.append("|").append(residentId).append(":UNSAFE");
            }

        }
        return answer.toString();

    }
    private Resident findResidentById(String residentId){
        for(Resident r: residents){
            if(r.getId().equals(residentId)){
                return r;
            }
        }
        return null;
    }


    public HealthCheckResult validateResidentForActivity(Resident resident, Activity activity) {
        HealthCheckResult result = new HealthCheckResult(true);

        if (resident == null) {
            result.addProblem("Resident is null.");
            return result;
        }

        if (activity == null) {
            result.addProblem("Activity is null.");
            return result;
        }

        MobilityLevel residentMobility = resident.getHealthProfile().getMobilityLevel();
        MobilityLevel requiredMobility = activity.getRequiredMobilityLevel();

        if (!hasEnoughMobility(residentMobility, requiredMobility)) {
            result.addProblem(
                    "Resident " + resident.getName()
                            + " has mobility level " + residentMobility
                            + ", but activity " + activity.getName()
                            + " requires " + requiredMobility + "."
            );
        }

        checkTextLimitationsForActivity(result, resident, activity);

        if (result.isSafe()) {
            result.addNote(
                    "Resident " + resident.getName()
                            + " can safely join activity " + activity.getName() + "."
            );
        }

        return result;
    }

    public HealthCheckResult validateResidentForRoom(Resident resident, Room room) {
        HealthCheckResult result = new HealthCheckResult(true);

        if (resident == null) {
            result.addProblem("Resident is null.");
            return result;
        }

        if (room == null) {
            result.addProblem("Room is null.");
            return result;
        }

        MobilityLevel residentMobility = resident.getHealthProfile().getMobilityLevel();

        if (residentMobility == MobilityLevel.LOW && !room.isAccessible()) {
            result.addProblem(
                    "Resident " + resident.getName()
                            + " has LOW mobility, but room "
                            + room.getName()
                            + " is not accessible."
            );
        }

        if (result.isSafe()) {
            result.addNote(
                    "Room " + room.getName()
                            + " is safe for resident " + resident.getName() + "."
            );
        }

        return result;
    }

    public HealthCheckResult validateResidentForEvent(Resident resident, EventProposal proposal) {
        HealthCheckResult finalResult = new HealthCheckResult(true);

        if (proposal == null) {
            finalResult.addProblem("Event proposal is null.");
            return finalResult;
        }

        HealthCheckResult activityResult = validateResidentForActivity(
                resident,
                proposal.getActivity()
        );

        HealthCheckResult roomResult = validateResidentForRoom(
                resident,
                proposal.getRoom()
        );

        finalResult.merge(activityResult);
        finalResult.merge(roomResult);

        if (finalResult.isSafe()) {
            finalResult.addNote(
                    "Resident " + resident.getName()
                            + " passed all health checks for proposal "
                            + proposal.getId() + "."
            );
        }

        return finalResult;
    }

    public Map<String, HealthCheckResult> validateProposalForResidents(
            EventProposal proposal,
            List<Resident> residents
    ) {
        Map<String, HealthCheckResult> results = new LinkedHashMap<>();

        if (proposal == null || residents == null) {
            return results;
        }

        for (Resident resident : residents) {
            if (proposal.getParticipantStatuses().containsKey(resident.getId())) {
                HealthCheckResult result = validateResidentForEvent(resident, proposal);
                results.put(resident.getId(), result);
            }
        }

        return results;
    }

    public boolean isEventSafeForResident(Resident resident, EventProposal proposal) {
        return validateResidentForEvent(resident, proposal).isSafe();
    }

    private boolean hasEnoughMobility(
            MobilityLevel residentMobility,
            MobilityLevel requiredMobility
    ) {
        return mobilityScore(residentMobility) >= mobilityScore(requiredMobility);
    }

    private int mobilityScore(MobilityLevel mobilityLevel) {
        if (mobilityLevel == MobilityLevel.LOW) {
            return 1;
        }

        if (mobilityLevel == MobilityLevel.MEDIUM) {
            return 2;
        }

        if (mobilityLevel == MobilityLevel.HIGH) {
            return 3;
        }

        return 0;
    }

    private void checkTextLimitationsForActivity(
            HealthCheckResult result,
            Resident resident,
            Activity activity
    ) {
        List<String> limitations = resident.getHealthProfile().getLimitations();

        if (limitations == null || limitations.isEmpty()) {
            return;
        }

        for (String limitation : limitations) {
            if (limitation == null) {
                continue;
            }

            String normalizedLimitation = limitation.toLowerCase();

            if (mentionsAvoidingWalking(normalizedLimitation)
                    && activity.getType() == ActivityType.WALKING) {
                result.addProblem(
                        "Resident " + resident.getName()
                                + " has limitation '" + limitation
                                + "', so walking activity is not safe."
                );
            }

            if (mentionsAvoidingExercise(normalizedLimitation)
                    && activity.getType() == ActivityType.FITNESS) {
                result.addProblem(
                        "Resident " + resident.getName()
                                + " has limitation '" + limitation
                                + "', so fitness activity is not safe."
                );
            }

            if (mentionsAvoidingNoise(normalizedLimitation)
                    && isNoisyActivity(activity)) {
                result.addProblem(
                        "Resident " + resident.getName()
                                + " has limitation '" + limitation
                                + "', so noisy activity may not be safe."
                );
            }
        }
    }

    private boolean mentionsAvoidingWalking(String limitation) {
        return limitation.contains("avoid walking")
                || limitation.contains("avoid long walking")
                || limitation.contains("no walking")
                || limitation.contains("cannot walk")
                || limitation.contains("walking limitation");
    }

    private boolean mentionsAvoidingExercise(String limitation) {
        return limitation.contains("avoid exercise")
                || limitation.contains("avoid fitness")
                || limitation.contains("no exercise")
                || limitation.contains("no fitness")
                || limitation.contains("heart condition");
    }

    private boolean mentionsAvoidingNoise(String limitation) {
        return limitation.contains("avoid noise")
                || limitation.contains("avoid loud")
                || limitation.contains("sensitive to noise");
    }

    private boolean isNoisyActivity(Activity activity) {
        return activity.getType() == ActivityType.MUSIC
                || activity.getType() == ActivityType.MOVIE
                || activity.getType() == ActivityType.BINGO;
    }

    public static class HealthCheckResult {
        private boolean safe;
        private List<String> messages;

        public HealthCheckResult(boolean safe) {
            this.safe = safe;
            this.messages = new ArrayList<>();
        }

        public boolean isSafe() {
            return safe;
        }

        public List<String> getMessages() {
            return messages;
        }

        public void addProblem(String message) {
            this.safe = false;
            this.messages.add(message);
        }

        public void addNote(String message) {
            this.messages.add(message);
        }

        public void merge(HealthCheckResult other) {
            if (other == null) {
                return;
            }

            if (!other.isSafe()) {
                this.safe = false;
            }

            this.messages.addAll(other.getMessages());
        }

        @Override
        public String toString() {
            return "HealthCheckResult{" +
                    "safe=" + safe +
                    ", messages=" + messages +
                    '}';
        }
    }


}
