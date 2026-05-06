package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import org.example.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ActivityAgent extends Agent {

    private List<Activity>  activities;
    private List<EventProposal> eventProposals;

    @Override
    protected void setup(){
        System.out.println(getLocalName() + " started.");

        activities = new ArrayList<>();
        eventProposals = new ArrayList<>();

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                initializeActivities();
                initializeSampleEventProposals();

                printActivities();
                printEventProposals();

            }
        });

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
