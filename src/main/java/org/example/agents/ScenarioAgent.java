package org.example.agents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.example.model.Activity;
import org.example.model.ActivityType;
import org.example.model.MobilityLevel;

import jade.core.Agent;

public class ScenarioAgent extends Agent {
    private static final String ConverstationId = "activity-proposal-create";
    private final ObjectMapper mapper = new ObjectMapper();
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
}
