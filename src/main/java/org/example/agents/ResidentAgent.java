package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import org.example.model.Activity;
import org.example.model.ActivityType;
import org.example.model.Resident;
import org.example.model.TimeSlot;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ResidentAgent extends Agent {
    private Resident resident;
    private List<TimeSlot> availableTimeSlots;


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

            }
        });

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

}
