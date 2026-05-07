package org.example.model;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventProposal {
    private String id;
    private Activity activity;
    private Room room;
    private TimeSlot timeSlot;

    private Map<String, ParticipationStatus> participantStatuses;
    private Map<String, Integer> requiredResources;
    private boolean approved;


    public EventProposal(String id, TimeSlot timeSlot, Activity activity, Room room) {
        this.id = id;
        this.participantStatuses = new LinkedHashMap<>();
        this.requiredResources = new LinkedHashMap<>();
        this.approved = false;
        this.timeSlot = timeSlot;
        this.activity = activity;
        this.room = room;
    }

    public Activity getActivity() {
        return activity;
    }

    public String getId() {
        return id;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public Room getRoom() {
        return room;
    }

    public boolean isApproved() {
        return approved;
    }

    public Map<String, ParticipationStatus> getParticipantStatuses() {
        return participantStatuses;
    }

    public Map<String, Integer> getRequiredResources() {
        return requiredResources;
    }

    public void addRequiredResource(String resourceId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Required resource quantity must be positive.");
        }

        requiredResources.put(resourceId, quantity);
    }

    public void suggestResident(Resident resident) {
        participantStatuses.put(resident.getId(), ParticipationStatus.SUGGESTED);
    }

    public void inviteResident(Resident resident) {
        participantStatuses.put(resident.getId(), ParticipationStatus.INVITED);
    }

    public void updateParticipationStatus(String residentId, ParticipationStatus status) {
        participantStatuses.put(residentId, status);
    }

    public int getConfirmedParticipantCount() {
        int count = 0;

        for (ParticipationStatus status : participantStatuses.values()) {
            if (status == ParticipationStatus.CONFIRMED) {
                count++;
            }
        }

        return count;
    }

    public boolean hasFreePlaces() {
        int confirmedCount = getConfirmedParticipantCount();

        return confirmedCount < activity.getMaxParticipants()
                && confirmedCount < room.getCapacity();
    }

    public void approve() {
        this.approved = true;
    }

    public void cancelApproval() {
        this.approved = false;
    }

    @Override
    public String toString() {
        return "EventProposal{" +
                "id='" + id + '\'' +
                ", activity=" + activity +
                ", room=" + room +
                ", timeSlot=" + timeSlot +
                ", requiredResources=" + requiredResources +
                ", participantStatuses=" + participantStatuses +
                ", approved=" + approved +
                '}';
    }
}
