package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import org.example.model.*;

import java.util.*;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.time.LocalDateTime;

public class ResourceAgent extends Agent {

    private List<Room> rooms;
    private Map<String, List<TimeSlot>> roomBookings;

    private List<Resource> resources;
    private Map<String, List<ResourceBooking>> resourceBookings;

    private static final String RESOURCE_BOOKING_CONVERSATION = "resource-booking";

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        rooms = new ArrayList<>();
        roomBookings = new HashMap<>();

        resources = new ArrayList<>();
        resourceBookings = new HashMap<>();

        initializeRooms();
        initializeResources();

        addResourceRequestReceiver();

        printRooms();
        printResources();
        printRoomBookings();
        printResourceBookings();


    }

    // communication with ActivityAgent
    private void addResourceRequestReceiver(){
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(RESOURCE_BOOKING_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
                );

                ACLMessage message = myAgent.receive(template);

                if(message == null){
                    block();
                }else{
                    handleResourceRequest(message);
                }

            }
        });
    }

    private void handleResourceRequest(ACLMessage message){
        String content = message.getContent();

        System.out.println(getLocalName() + " <- " + message.getSender().getLocalName() + ": booking request: " + content);

        String replyContent;

        try{
            String[] parts = content.split("\\|");

            if (parts.length < 6) {
                replyContent = "UNKNOWN|REJECTED|Wrong message format";
            } else{
                String proposalId = parts[0];
                String roomId = parts[1];
                int participantCount = Integer.parseInt(parts[2]);

                LocalDateTime startTime = LocalDateTime.parse(parts[3]);
                LocalDateTime endTime = LocalDateTime.parse(parts[4]);
                TimeSlot timeSlot = new TimeSlot(startTime, endTime);

                Map<String, Integer> requiredResources = parseRequiredResources(parts[5]);

                boolean canBook = isRoomAvailable(roomId, timeSlot) && hasEnoughRoomCapacity(roomId, participantCount) && areResourcesAvailable(requiredResources, timeSlot);

                if(canBook){
                    bookRoom(roomId, timeSlot);
                    bookResources(requiredResources,timeSlot);

                    replyContent = proposalId + "|ACCEPTED";

                    System.out.println("ResourceAgent accepted proposal " + proposalId);
                    printRoomBookings();
                    printResourceBookings();
                }else{
                    replyContent = proposalId + "|REJECTED|Room or resources not available";

                    System.out.println("ResourceAgent rejected proposal " + proposalId);
                }

            }

        } catch (Exception e) {
            replyContent = "UNKNOWN|REJECTED|Invalid booking request";
        }

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(RESOURCE_BOOKING_CONVERSATION);
        reply.setContent(replyContent);

        send(reply);

    }

    private Map<String,Integer> parseRequiredResources(String resourcesPart){
        Map<String, Integer> requiredResources = new HashMap<>();

        if (resourcesPart == null || resourcesPart.isBlank() || resourcesPart.equals("-")) {
            return requiredResources;
        }

        String[] resources = resourcesPart.split(",");

        for (String resource : resources) {
            String[] parts = resource.split(":");

            if (parts.length == 2) {
                String resourceId = parts[0];
                int quantity = Integer.parseInt(parts[1]);

                requiredResources.put(resourceId, quantity);
            }
        }
        return requiredResources;


    }

    private void initializeRooms() {
        //START: MOCK DATA - REMOVE THIS LATER
        addRoom(new Room("ROOM1", "Common Room", 15, true));
        addRoom(new Room("ROOM2", "Garden Area", 10, false));
        addRoom(new Room("ROOM3", "Therapy Room", 6, true));
        addRoom(new Room("ROOM4", "Dining Hall", 25, true));
        //END: MOCK DATA - REMOVE THIS LATER
    }

    private void initializeResources() {
        //START: MOCK DATA - REMOVE THIS LATER
        addResource(new Resource("RES-YOGA-MAT", "Yoga Mat", 12));
        addResource(new Resource("RES-POKER-CARDS", "Poker Card Deck", 4));
        addResource(new Resource("RES-BOARD-GAME", "Board Game Set", 8));
        addResource(new Resource("RES-BINGO-SET", "Bingo Set", 2));
        addResource(new Resource("RES-SPEAKER", "Portable Speaker", 1));
        addResource(new Resource("RES-KNITTING-SET", "Knitting Set", 10));
        //END: MOCK DATA - REMOVE THIS LATER
    }

    public void addRoom(Room room) {
        rooms.add(room);
        roomBookings.put(room.getId(), new ArrayList<>());
    }

    public void addResource(Resource resource) {
        resources.add(resource);
        resourceBookings.put(resource.getId(), new ArrayList<>());
    }

    public Optional<Room> findRoomById(String roomId) {
        return rooms.stream()
                .filter(room -> room.getId().equals(roomId))
                .findFirst();
    }

    public Optional<Resource> findResourceById(String resourceId) {
        return resources.stream()
                .filter(resource -> resource.getId().equals(resourceId))
                .findFirst();
    }

    public boolean isRoomAvailable(String roomId, TimeSlot requestedSlot) {
        List<TimeSlot> bookings = roomBookings.get(roomId);

        if (bookings == null) {
            return false;
        }

        for (TimeSlot bookedSlot : bookings) {
            if (bookedSlot.overlapsWith(requestedSlot)) {
                return false;
            }
        }

        return true;
    }

    public boolean hasEnoughRoomCapacity(String roomId, int participantCount) {
        Optional<Room> optionalRoom = findRoomById(roomId);

        if (optionalRoom.isEmpty()) {
            return false;
        }

        return optionalRoom.get().getCapacity() >= participantCount;
    }

    public boolean bookRoom(String roomId, TimeSlot timeSlot) {
        Optional<Room> optionalRoom = findRoomById(roomId);

        if (optionalRoom.isEmpty()) {
            System.out.println("Room not found: " + roomId);
            return false;
        }

        if (!isRoomAvailable(roomId, timeSlot)) {
            System.out.println("Room is not available: " + optionalRoom.get().getName());
            return false;
        }

        roomBookings.get(roomId).add(timeSlot);

        System.out.println("Booked room: " + optionalRoom.get().getName() + " at " + timeSlot);
        return true;
    }

    public int getAvailableResourceQuantity(String resourceId, TimeSlot requestedSlot) {
        Optional<Resource> optionalResource = findResourceById(resourceId);

        if (optionalResource.isEmpty()) {
            return 0;
        }

        int totalQuantity = optionalResource.get().getTotalQuantity();
        int bookedQuantity = 0;

        List<ResourceBooking> bookings = resourceBookings.get(resourceId);

        if (bookings == null) {
            return 0;
        }

        for (ResourceBooking booking : bookings) {
            if (booking.overlapsWith(requestedSlot)) {
                bookedQuantity += booking.getQuantity();
            }
        }

        return totalQuantity - bookedQuantity;
    }

    public boolean isResourceAvailable(String resourceId, int requestedQuantity, TimeSlot requestedSlot) {
        if (requestedQuantity <= 0) {
            return false;
        }

        return getAvailableResourceQuantity(resourceId, requestedSlot) >= requestedQuantity;
    }

    public boolean areResourcesAvailable(Map<String, Integer> requiredResources, TimeSlot requestedSlot) {
        for (Map.Entry<String, Integer> entry : requiredResources.entrySet()) {
            String resourceId = entry.getKey();
            int requestedQuantity = entry.getValue();

            if (!isResourceAvailable(resourceId, requestedQuantity, requestedSlot)) {
                return false;
            }
        }

        return true;
    }

    public boolean bookResource(String resourceId, int quantity, TimeSlot timeSlot) {
        Optional<Resource> optionalResource = findResourceById(resourceId);

        if (optionalResource.isEmpty()) {
            System.out.println("Resource not found: " + resourceId);
            return false;
        }

        if (!isResourceAvailable(resourceId, quantity, timeSlot)) {
            System.out.println("Not enough available quantity for resource: " + optionalResource.get().getName());
            return false;
        }

        ResourceBooking booking = new ResourceBooking(resourceId, quantity, timeSlot);
        resourceBookings.get(resourceId).add(booking);

        System.out.println("Booked resource: " + optionalResource.get().getName()
                + ", quantity: " + quantity
                + ", time: " + timeSlot);

        return true;
    }

    public boolean bookResources(Map<String, Integer> requiredResources, TimeSlot timeSlot) {
        if (!areResourcesAvailable(requiredResources, timeSlot)) {
            System.out.println("Not all required resources are available.");
            return false;
        }

        for (Map.Entry<String, Integer> entry : requiredResources.entrySet()) {
            bookResource(entry.getKey(), entry.getValue(), timeSlot);
        }

        return true;
    }

    public boolean canSupportProposal(EventProposal proposal) {
        int confirmedParticipants = proposal.getConfirmedParticipantCount();

        return isRoomAvailable(proposal.getRoom().getId(), proposal.getTimeSlot())
                && hasEnoughRoomCapacity(proposal.getRoom().getId(), confirmedParticipants)
                && areResourcesAvailable(proposal.getRequiredResources(), proposal.getTimeSlot());
    }

    public boolean bookResourcesForProposal(EventProposal proposal) {
        if (!canSupportProposal(proposal)) {
            System.out.println("ResourceAgent cannot support proposal: " + proposal.getId());
            return false;
        }

        boolean roomBooked = bookRoom(proposal.getRoom().getId(), proposal.getTimeSlot());
        boolean resourcesBooked = bookResources(proposal.getRequiredResources(), proposal.getTimeSlot());

        return roomBooked && resourcesBooked;
    }

    private void printRooms() {
        System.out.println("\nRooms managed by " + getLocalName() + ":");

        for (Room room : rooms) {
            System.out.println(" - " + room);
        }
    }

    private void printResources() {
        System.out.println("\nShared resources managed by " + getLocalName() + ":");

        for (Resource resource : resources) {
            System.out.println(" - " + resource);
        }
    }

    public void printRoomBookings() {
        System.out.println("\nRoom bookings:");

        for (Map.Entry<String, List<TimeSlot>> entry : roomBookings.entrySet()) {
            System.out.println("Room: " + entry.getKey());

            if (entry.getValue().isEmpty()) {
                System.out.println(" - No bookings.");
            } else {
                for (TimeSlot slot : entry.getValue()) {
                    System.out.println(" - " + slot);
                }
            }
        }
    }

    public void printResourceBookings() {
        System.out.println("\nResource bookings:");

        for (Map.Entry<String, List<ResourceBooking>> entry : resourceBookings.entrySet()) {
            System.out.println("Resource: " + entry.getKey());

            if (entry.getValue().isEmpty()) {
                System.out.println(" - No bookings.");
            } else {
                for (ResourceBooking booking : entry.getValue()) {
                    System.out.println(" - " + booking);
                }
            }
        }
    }

}
