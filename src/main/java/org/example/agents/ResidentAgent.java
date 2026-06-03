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

import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.MessageTemplate;
import org.example.model.MobilityLevel;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import org.example.model.HealthProfile;

public class ResidentAgent extends Agent {
    private Resident resident;
    private List<TimeSlot> availableTimeSlots;
    private List<TimeSlot> bookedTimeSlots;

    private static final String HEALTH_AGENT_NAME = "health-agent";
    private static final String RESIDENT_HEALTH_CONVERSATION = "resident-health-info";
    private static final String RESIDENT_INVITATION_CONVERSATION = "resident-activity-invitation";
    private static final String RESIDENT_BOOKING_CONFIRMATION_CONVERSATION = "resident-event-booked";
    @Override
    protected void setup(){
        System.out.println(getLocalName()+" started.");

        Object[] args = getArguments();

        if(args!=null && args.length > 0 && args[0] instanceof Resident)
        {
            resident = (Resident) args[0];
            availableTimeSlots = new ArrayList<>();
            bookedTimeSlots = new ArrayList<>();
        }else {
            System.out.println(getLocalName()+" has no resident data.");
            doDelete();
            return;
        }
        addActivityInvitationReceiver();
        addEventBookedNotificationReceiver();
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

    public boolean isAvailableAt(TimeSlot requestedTimeSlot) {
        boolean fitsAvailability = false;

        for (TimeSlot availableSlot : availableTimeSlots) {
            boolean requestedSlotFits =
                    !requestedTimeSlot.getStartTime().isBefore(availableSlot.getStartTime())
                            && !requestedTimeSlot.getEndTime().isAfter(availableSlot.getEndTime());

            if (requestedSlotFits) {
                fitsAvailability = true;
                break;
            }
        }

        if (!fitsAvailability) {
            return false;
        }

        for (TimeSlot bookedSlot : bookedTimeSlots) {
            if (bookedSlot.overlapsWith(requestedTimeSlot)) {
                return false;
            }
        }

        return true;
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
    //communication with the ActivityAgent
    private void addActivityInvitationReceiver() {
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESIDENT_INVITATION_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleActivityInvitation(message);
            }
        });
    }
    private void handleActivityInvitation(ACLMessage message) {
        String content = message.getContent();

        System.out.println(
                getLocalName() + " <- " + message.getSender().getLocalName()
                        + ": activity invitation: " + content
        );

        String replyContent;

        try {
            replyContent = evaluateActivityInvitation(content);
        } catch (Exception ex) {
            String residentId = resident != null ? resident.getId() : "UNKNOWN";

            replyContent = "UNKNOWN"
                    + "|" + residentId
                    + "|DECLINED|Invalid invitation format";

            System.err.println(getLocalName() + " failed to process invitation:");
            ex.printStackTrace();
        }

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(RESIDENT_INVITATION_CONVERSATION);
        reply.setContent(replyContent);

        send(reply);

        System.out.println(
                getLocalName() + " -> " + message.getSender().getLocalName()
                        + ": invitation answer: " + replyContent
        );
    }
    // communication with ActivityAgent: final booked-event notification
    private void addEventBookedNotificationReceiver() {
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESIDENT_BOOKING_CONFIRMATION_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleEventBookedNotification(message);
            }
        });
    }
    private void handleEventBookedNotification(ACLMessage message) {
        String content = message.getContent();

        System.out.println(
                getLocalName() + " <- " + message.getSender().getLocalName()
                        + ": event booked notification: " + content
        );

        try {
            registerBookedEvent(content);
        } catch (Exception ex) {
            System.err.println(getLocalName() + " failed to process booked-event notification:");
            ex.printStackTrace();
        }
    }
    private void registerBookedEvent(String content) {
        String[] parts = content.split("\\|");

        if (parts.length < 8) {
            throw new IllegalArgumentException(
                    "Wrong booked-event format. Expected: proposalId|activityId|activityName|activityType|startTime|endTime|roomId|roomName"
            );
        }

        String proposalId = parts[0];
        String activityName = parts[2];
        LocalDateTime startTime = LocalDateTime.parse(parts[4]);
        LocalDateTime endTime = LocalDateTime.parse(parts[5]);
        String roomName = parts[7];

        TimeSlot bookedSlot = new TimeSlot(startTime, endTime);

        for (TimeSlot existingBooking : bookedTimeSlots) {
            if (existingBooking.overlapsWith(bookedSlot)) {
                System.out.println(
                        getLocalName() + ": booked event " + proposalId
                                + " overlaps with an already stored booking. "
                                + "The notification is acknowledged but the slot is not duplicated."
                );
                return;
            }
        }

        bookedTimeSlots.add(bookedSlot);

        System.out.println(
                getLocalName() + ": event booked for resident "
                        + resident.getId()
                        + " -> " + activityName
                        + " in " + roomName
                        + " at " + bookedSlot
        );
    }





    private String evaluateActivityInvitation(String content) {
        String[] parts = content.split("\\|");

        if (parts.length < 9) {
            throw new IllegalArgumentException(
                    "Wrong invitation format. Expected: proposalId|residentId|activityId|activityName|activityType|maxParticipants|requiredMobilityLevel|startTime|endTime"
            );
        }

        String proposalId = parts[0];
        String targetResidentId = parts[1];

        ActivityType activityType = ActivityType.valueOf(parts[4]);

        TimeSlot timeSlot = new TimeSlot(
                LocalDateTime.parse(parts[7]),
                LocalDateTime.parse(parts[8])
        );

        if (resident == null) {
            return proposalId
                    + "|UNKNOWN"
                    + "|DECLINED|Resident data is not initialized";
        }

        if (!resident.getId().equals(targetResidentId)) {
            return proposalId
                    + "|" + resident.getId()
                    + "|DECLINED|Invitation was sent to wrong resident";
        }

        if (!resident.isWillingToParticipate()) {
            return proposalId
                    + "|" + resident.getId()
                    + "|DECLINED|Resident is not willing to participate";
        }

        if (!resident.getPreferences().contains(activityType)) {
            return proposalId
                    + "|" + resident.getId()
                    + "|DECLINED|Resident is not interested in this activity";
        }

        if (!isAvailableAt(timeSlot)) {
            return proposalId
                    + "|" + resident.getId()
                    + "|DECLINED|Resident is not available at this time";
        }

        return proposalId
                + "|" + resident.getId()
                + "|ACCEPTED|Resident accepts invitation";
    }

}
