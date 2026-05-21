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
import jade.lang.acl.MessageTemplate;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.HashMap;

public class ScenarioAgent extends Agent {
    private static final String ConverstationId = "activity-proposal-create";
    private static final String SCENARIO_TO_ACTIVITY_CONVERSATION = "scenario-event-proposal";
    private static final String ACTIVITY_AGENT_NAME = "activity-agent";
    private static final String SOCIAL_SUPPORT_AGENT_NAME = "social-support-agent";
    private static final String SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION = "scenario-social-suggestions";
    private static final String RESOURCE_AGENT_NAME = "resource-agent";
    private static final String ACTIVITY_RESOURCE_REQUIREMENTS_CONVERSATION = "activity-resource-requirements";
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    protected void setup(){
        System.out.println(getLocalName()+"started");
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action(){
                MessageTemplate template = MessageTemplate.MatchConversationId(ConverstationId);
                ACLMessage msg = receive(template);
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


        askSocialSupportAgentForSuggestedResidents(proposal);
        askResourceAgentForRequiredResources(proposal);

        return proposal;
    }
    // communication with SocialSupportAgent
    private void askSocialSupportAgentForSuggestedResidents(EventProposal proposal) {
        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

        String replyWith = "social-suggestions-" + proposal.getId() + "-" + System.currentTimeMillis();

        message.addReceiver(new AID(SOCIAL_SUPPORT_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION);
        message.setReplyWith(replyWith);
        message.setContent(buildSocialSuggestionRequest(proposal));

        send(message);

        System.out.println(
                getLocalName() + " -> " + SOCIAL_SUPPORT_AGENT_NAME
                        + ": asking resident suggestions for proposal " + proposal.getId()
        );

        MessageTemplate responseTemplate = MessageTemplate.and(
                MessageTemplate.MatchConversationId(SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION),
                MessageTemplate.MatchInReplyTo(replyWith)
        );

        ACLMessage response = blockingReceive(responseTemplate, 5000);

        if (response == null) {
            System.out.println(
                    getLocalName() + ": no response from " + SOCIAL_SUPPORT_AGENT_NAME
                            + " for proposal " + proposal.getId()
            );
            return;
        }

        System.out.println(
                getLocalName() + " <- " + SOCIAL_SUPPORT_AGENT_NAME
                        + ": " + response.getContent()
        );

        applySocialSupportSuggestions(proposal, response.getContent());
    }
    private String buildSocialSuggestionRequest(EventProposal proposal) {
        Activity activity = proposal.getActivity();

        return proposal.getId()
                + "|" + activity.getId()
                + "|" + activity.getName()
                + "|" + activity.getType()
                + "|" + activity.getMaxParticipants()
                + "|" + activity.getRequiredMobilityLevel();
    }
    private void applySocialSupportSuggestions(EventProposal proposal, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        String[] parts = content.split("\\|");

        if (parts.length < 2) {
            System.out.println("Wrong social suggestion response format.");
            return;
        }

        String responseProposalId = parts[0];

        if (!proposal.getId().equals(responseProposalId)) {
            System.out.println(
                    "Social suggestion response does not match proposal "
                            + proposal.getId()
            );
            return;
        }

        String residentsPart = parts[1];

        if (residentsPart == null || residentsPart.isBlank() || residentsPart.equals("-")) {
            System.out.println("No residents suggested for proposal " + proposal.getId());
            return;
        }

        String[] residentIds = residentsPart.split(",");

        for (String residentId : residentIds) {
            proposal.updateParticipationStatus(
                    residentId,
                    ParticipationStatus.SUGGESTED
            );

            System.out.println(
                    "Added suggested resident " + residentId
                            + " to proposal " + proposal.getId()
            );
        }
    }
    // communication with ResourceAgent
    private void askResourceAgentForRequiredResources(EventProposal proposal) {
        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

        String replyWith = "resource-requirements-"
                + proposal.getId()
                + "-"
                + System.currentTimeMillis();

        message.addReceiver(new AID(RESOURCE_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(ACTIVITY_RESOURCE_REQUIREMENTS_CONVERSATION);
        message.setReplyWith(replyWith);
        message.setContent(buildResourceRequirementsRequest(proposal));

        send(message);

        System.out.println(
                getLocalName() + " -> " + RESOURCE_AGENT_NAME
                        + ": asking required resources for proposal " + proposal.getId()
        );

        MessageTemplate responseTemplate = MessageTemplate.and(
                MessageTemplate.MatchConversationId(ACTIVITY_RESOURCE_REQUIREMENTS_CONVERSATION),
                MessageTemplate.MatchInReplyTo(replyWith)
        );

        ACLMessage response = blockingReceive(responseTemplate, 5000);

        if (response == null) {
            System.out.println(
                    getLocalName() + ": no response from " + RESOURCE_AGENT_NAME
                            + " for proposal " + proposal.getId()
            );
            return;
        }

        System.out.println(
                getLocalName() + " <- " + RESOURCE_AGENT_NAME
                        + ": " + response.getContent()
        );

        applyRequiredResources(proposal, response.getContent());
    }
    private void applyRequiredResources(EventProposal proposal, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        String[] parts = content.split("\\|");

        if (parts.length < 2) {
            System.out.println("Wrong resource requirements response format.");
            return;
        }

        String responseProposalId = parts[0];

        if (!proposal.getId().equals(responseProposalId)) {
            System.out.println(
                    "Resource requirements response does not match proposal "
                            + proposal.getId()
            );
            return;
        }

        String resourcesPart = parts[1];

        if (resourcesPart == null || resourcesPart.isBlank() || resourcesPart.equals("-")) {
            System.out.println("No shared resources required for proposal " + proposal.getId());
            return;
        }

        Map<String, Integer> requiredResources = parseRequiredResources(resourcesPart);

        for (Map.Entry<String, Integer> entry : requiredResources.entrySet()) {
            proposal.addRequiredResource(entry.getKey(), entry.getValue());

            System.out.println(
                    "Added required resource "
                            + entry.getKey()
                            + " x"
                            + entry.getValue()
                            + " to proposal "
                            + proposal.getId()
            );
        }
    }
    private String buildResourceRequirementsRequest(EventProposal proposal) {
        return proposal.getId()
                + "|" + proposal.getActivity().getType();
    }
    private Map<String, Integer> parseRequiredResources(String resourcesPart) {
        Map<String, Integer> requiredResources = new HashMap<>();

        if (resourcesPart == null || resourcesPart.isBlank() || resourcesPart.equals("-")) {
            return requiredResources;
        }

        String[] resources = resourcesPart.split(",");

        for (String resource : resources) {
            String[] parts = resource.split(":");

            if (parts.length == 2) {
                String resourceId = parts[0].trim();
                int quantity = Integer.parseInt(parts[1].trim());

                requiredResources.put(resourceId, quantity);
            }
        }

        return requiredResources;
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
