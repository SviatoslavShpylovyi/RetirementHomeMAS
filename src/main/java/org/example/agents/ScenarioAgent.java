package org.example.agents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.example.model.Activity;
import org.example.model.ActivityType;
import org.example.model.MobilityLevel;
import jade.core.AID;
import org.example.model.EventProposal;
import org.example.model.Room;
import org.example.model.TimeSlot;
import org.example.model.ParticipationStatus;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ScenarioAgent extends Agent {
    private static final String ConverstationId = "activity-proposal-create";
    private static final String SCENARIO_TO_ACTIVITY_CONVERSATION = "scenario-event-proposal";
    private static final String ACTIVITY_AGENT_NAME = "activity-agent";
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @Override
    protected void setup(){
        System.out.println(getLocalName()+"started");
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action(){
                ACLMessage msg = receive();
                if(msg == null){
                    block();
                    return;
                }
                if(!ConverstationId.equals(msg.getConversationId())){
                    return;
                }
                try{
                    String json = msg.getContent();
                    System.out.println("ScenarioAgent received JSON:");
                    System.out.println(json);
                    JsonNode root = mapper.readTree(json);
                    JsonNode payload = root.get("payload");
                    if(payload ==null){
                        System.err.println("Missing payload field");
                        return;
                    }
                    Activity activity = parseActivity(payload);
                    System.out.println("Parsed activity:");
                    System.out.println(activity);
                    EventProposal proposal = createEventProposal(activity);

                    System.out.println("Created event proposal:");
                    System.out.println(proposal);

                    sendProposalToActivityAgent(proposal);
                }
                catch (Exception ex){
                    System.err.println("ScenarioAgent failed to process JSON:");
                    ex.printStackTrace();
                }
            }
        });
    }
    private Activity parseActivity(JsonNode payload){
        String id = payload.get("id").asText();
        String name = payload.get("name").asText();
        ActivityType type = ActivityType.valueOf(
                payload.get("type").asText()
        );
        int maxParticipants = payload.get("maxParticipants").asInt();
        MobilityLevel mobilityLevel = MobilityLevel.valueOf(
                payload.get("requiredMobilityLevel").asText()
        );
        return new Activity(
                id,
                name,
                type,
                maxParticipants,
                mobilityLevel
        );
    }
    private EventProposal createEventProposal(Activity activity) {
        String proposalId = "E-" + activity.getId();

        Room room = chooseDefaultRoom(activity);

        TimeSlot timeSlot = new TimeSlot(
                LocalDateTime.of(2026, 5, 7, 15, 0),
                LocalDateTime.of(2026, 5, 7, 16, 0)
        );

        EventProposal proposal = new EventProposal(
                proposalId,
                timeSlot,
                activity,
                room
        );


        addTemporarySuggestedResidents(proposal, activity);
        addDefaultRequiredResources(proposal, activity);

        return proposal;
    }
    private void addTemporarySuggestedResidents(EventProposal proposal, Activity activity) {
        switch (activity.getType()) {
            case MUSIC, SOCIAL_TEA, BINGO -> {
                proposal.updateParticipationStatus("R1", ParticipationStatus.SUGGESTED);
            }

            case BOARD_GAMES, POKER, READING -> {
                proposal.updateParticipationStatus("R2", ParticipationStatus.SUGGESTED);
            }

            case ART, KNITTING, CROCHETING, MOVIE -> {
                proposal.updateParticipationStatus("R3", ParticipationStatus.SUGGESTED);
            }

            default -> {
                proposal.updateParticipationStatus("R1", ParticipationStatus.SUGGESTED);
                proposal.updateParticipationStatus("R2", ParticipationStatus.SUGGESTED);
            }
        }
    }
    private void addDefaultRequiredResources(EventProposal proposal, Activity activity) {
        switch (activity.getType()) {
            case MUSIC, MOVIE -> {
                proposal.addRequiredResource("RES-SPEAKER", 1);
            }

            case BOARD_GAMES -> {
                proposal.addRequiredResource("RES-BOARD-GAME", 1);
            }

            case POKER -> {
                proposal.addRequiredResource("RES-POKER-CARDS", 1);
            }

            case BINGO -> {
                proposal.addRequiredResource("RES-BINGO-SET", 1);
            }

            case KNITTING, CROCHETING -> {
                proposal.addRequiredResource("RES-KNITTING-SET", 1);
            }

            case FITNESS -> {
                proposal.addRequiredResource("RES-YOGA-MAT", 1);
            }

            default -> {
                // No special resources required.
            }
        }
    }
    private Room chooseDefaultRoom(Activity activity) {
        if (activity.getMaxParticipants() > 15) {
            return new Room(
                    "ROOM4",
                    "Dining Hall",
                    25,
                    true
            );
        }

        return new Room(
                "ROOM1",
                "Common Room",
                15,
                true
        );
    }

    private void sendProposalToActivityAgent(EventProposal proposal) throws Exception {
        ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);

        message.addReceiver(new AID(ACTIVITY_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(SCENARIO_TO_ACTIVITY_CONVERSATION);
        message.setLanguage("JSON");
        message.setContent(mapper.writeValueAsString(proposal));

        send(message);

        System.out.println(
                getLocalName() + " -> " + ACTIVITY_AGENT_NAME +
                        ": sent event proposal " + proposal.getId()
        );
    }
}
