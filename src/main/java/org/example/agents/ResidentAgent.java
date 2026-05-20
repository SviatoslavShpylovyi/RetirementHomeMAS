package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import org.example.model.Activity;
import org.example.model.ActivityType;
import org.example.model.Resident;
import org.example.model.TimeSlot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import org.example.model.HealthProfile;

public class ResidentAgent extends Agent {
    private Resident resident;
    private List<TimeSlot> availableTimeSlots;

    private static final String HEALTH_AGENT_NAME = "health-agent";
    private static final String RESIDENT_HEALTH_CONVERSATION = "resident-health-info";

    @Override
    protected void setup(){
        System.out.println(getLocalName()+" started.");

        Object[] args = getArguments();

        if(args!=null && args.length > 0 && args[0] instanceof Resident)
        {
            resident = (Resident) args[0];
            availableTimeSlots = new ArrayList<>();
        }else {
            System.out.println(getLocalName()+" has no resident data.");
            doDelete();
            return;
        }

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (availableTimeSlots.isEmpty()) {
                    initializeAvailability();
                }

                printResidentData();
                sendAddResidentHealthInfo();

            }
        });

    }

    //communication with HealthAgent
    private void sendResidentHealthInfo(String action){
        ACLMessage message = new ACLMessage(ACLMessage.INFORM);

        message.addReceiver(new AID(HEALTH_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(RESIDENT_HEALTH_CONVERSATION);
        message.setContent(buildResidentHealthInfoMessage(action));

        send(message);


        System.out.println(getLocalName() + " -> " + HEALTH_AGENT_NAME  + ": " + action + " health info for resident " + resident.getId());
    }

    private void sendAddResidentHealthInfo(){
        sendResidentHealthInfo("ADD");
    }

    private void sendUpdateResidentHealthInfo(){
        sendResidentHealthInfo("UPDATE");
    }

    private String buildResidentHealthInfoMessage(String action){

        HealthProfile healthProfile = resident.getHealthProfile();

        return action
                + "|" + resident.getId()
                + "|" + resident.getName()
                + "|" + resident.isWillingToParticipate()
                + "|" + healthProfile.getMobilityLevel()
                + "|" + buildLimitationsPart();
    }

    private String buildLimitationsPart(){
        List<String> limitations = resident.getHealthProfile().getLimitations();

        if (limitations == null || limitations.isEmpty()) {
            return "-";
        }

        StringBuilder builder = new StringBuilder();

        for (String limitation : limitations) {
            if (builder.length() > 0) {
                builder.append(";");
            }

            builder.append(limitation);
        }

        return builder.toString();
    }

    private void sendRemoveResidentHealthInfo(){
        ACLMessage message = new ACLMessage(ACLMessage.INFORM);

        message.addReceiver(new AID(HEALTH_AGENT_NAME, AID.ISLOCALNAME));
        message.setConversationId(RESIDENT_HEALTH_CONVERSATION);
        message.setContent("REMOVE|" + resident.getId());

        send(message);

        System.out.println(getLocalName() + " -> " + HEALTH_AGENT_NAME
                + ": remove health info for resident " + resident.getId());
    }


    private void initializeAvailability(){
        availableTimeSlots = new ArrayList<>();

        //START: MOCK DATA - REMOVE THIS LATER
        availableTimeSlots.add(new TimeSlot(
                LocalDateTime.of(2026, 5, 7, 10, 0),
                LocalDateTime.of(2026, 5, 7, 12, 0)
        ));

        availableTimeSlots.add(new TimeSlot(
                LocalDateTime.of(2026, 5, 7, 15, 0),
                LocalDateTime.of(2026, 5, 7, 17, 0)
        ));
        //END: MOCK DATA - REMOVE THIS LATER

    }


    private void printResidentData()
    {
        System.out.println("\nResident represented by " + getLocalName() + ":");
        System.out.println(" - " + resident);

        System.out.println("Available time slots:");
        for (TimeSlot timeSlot : availableTimeSlots) {
            System.out.println(" - " + timeSlot);
        }

    }

    public Resident getResident() {
        return resident;
    }

    public boolean isInterestedIn(Activity activity)
    {
        return resident.isWillingToParticipate() && resident.getPreferences().contains(activity.getType());
    }

    public boolean isAvailableAt(TimeSlot requestedTimeSlot){
        for(TimeSlot availableSlot : availableTimeSlots)
        {
            boolean requestedSlotFits = !requestedTimeSlot.getStartTime().isBefore(availableSlot.getStartTime()) &&
                    !requestedTimeSlot.getEndTime().isAfter(availableSlot.getEndTime());

            if(requestedSlotFits)
            {
                return true;
            }
        }
        return false;
    }

    public void updateHealthProfile(HealthProfile newHealthProfile){
        if (newHealthProfile == null) {
            System.out.println("Cannot update health profile to null.");
            return;
        }

        resident.setHealthProfile(newHealthProfile);

        System.out.println(getLocalName() + ": health profile updated for resident "
                + resident.getId());

        sendUpdateResidentHealthInfo();
    }



    @Override
    protected void takeDown(){
        if (resident != null) {
            sendRemoveResidentHealthInfo();
        }

        System.out.println(getLocalName() + " stopped.");
    }

}
