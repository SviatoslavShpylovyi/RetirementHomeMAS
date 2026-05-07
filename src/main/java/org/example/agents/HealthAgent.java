package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import org.example.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HealthAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");


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
