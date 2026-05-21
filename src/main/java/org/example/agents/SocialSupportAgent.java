package org.example.agents;

import jade.core.Agent;
import org.example.model.Activity;
import org.example.model.EventProposal;
import org.example.model.ParticipationStatus;
import org.example.model.Resident;

import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.example.model.ActivityType;
import org.example.model.MobilityLevel;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocialSupportAgent extends Agent {

    private Map<String, Integer> participationCounts;
    private Map<String, Integer> declinedCounts;
    private List<String> supportLog;
    private List<Resident> knownResidents;
    private static final String SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION = "scenario-social-suggestions";
    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        participationCounts = new HashMap<>();
        declinedCounts = new HashMap<>();
        supportLog = new ArrayList<>();
        knownResidents = new ArrayList<>();

        Object[] args = getArguments();

        if (args != null && args.length > 0 && args[0] instanceof List<?>) {
            for (Object item : (List<?>) args[0]) {
                if (item instanceof Resident) {
                    knownResidents.add((Resident) item);
                }
            }
        }

        System.out.println(getLocalName() + " knows residents:");
        for (Resident resident : knownResidents) {
            System.out.println(" - " + resident);
        }

        addScenarioSuggestionRequestReceiver();
    }

    public boolean isActivityMatchingPreferences(Resident resident, Activity activity) {
        if (resident == null || activity == null) {
            return false;
        }

        return resident.getPreferences().contains(activity.getType());
    }

    public boolean isResidentOpenToSuggestion(Resident resident) {
        if (resident == null) {
            return false;
        }

        return resident.isWillingToParticipate();
    }

    public int calculateSupportScore(Resident resident, Activity activity) {
        if (resident == null || activity == null) {
            return 0;
        }

        int score = 0;

        if (resident.isWillingToParticipate()) {
            score += 30;
        } else {
            score -= 30;
        }

        if (resident.getPreferences().contains(activity.getType())) {
            score += 40;
        }

        int participationCount = participationCounts.getOrDefault(resident.getId(), 0);

        if (participationCount == 0) {
            score += 30;
        } else if (participationCount <= 2) {
            score += 15;
        } else {
            score -= 10;
        }

        int declinedCount = declinedCounts.getOrDefault(resident.getId(), 0);

        if (declinedCount >= 3) {
            score -= 20;
        }

        return score;
    }

    public boolean shouldSuggestActivity(Resident resident, Activity activity) {
        return calculateSupportScore(resident, activity) >= 50;
    }

    public List<Activity> suggestActivitiesForResident(
            Resident resident,
            List<Activity> activities
    ) {
        List<Activity> suggestions = new ArrayList<>();

        if (resident == null || activities == null) {
            return suggestions;
        }

        for (Activity activity : activities) {
            if (shouldSuggestActivity(resident, activity)) {
                suggestions.add(activity);
            }
        }

        logSuggestionResult(resident, suggestions);

        return suggestions;
    }

    public List<Resident> suggestResidentsForActivity(
            Activity activity,
            List<Resident> residents
    ) {
        List<Resident> suggestedResidents = new ArrayList<>();

        if (activity == null || residents == null) {
            return suggestedResidents;
        }

        for (Resident resident : residents) {
            if (shouldSuggestActivity(resident, activity)) {
                suggestedResidents.add(resident);
            }
        }

        return suggestedResidents;
    }

    public void suggestResidentsForProposal(
            EventProposal proposal,
            List<Resident> residents
    ) {
        if (proposal == null || residents == null) {
            return;
        }

        Activity activity = proposal.getActivity();

        for (Resident resident : residents) {
            if (shouldSuggestActivity(resident, activity)) {
                proposal.updateParticipationStatus(
                        resident.getId(),
                        ParticipationStatus.SUGGESTED
                );

                supportLog.add(
                        "Suggested resident " + resident.getName()
                                + " for proposal " + proposal.getId()
                );
            }
        }
    }

    public void recordParticipationStatus(
            String residentId,
            ParticipationStatus status
    ) {
        if (residentId == null || status == null) {
            return;
        }

        if (status == ParticipationStatus.CONFIRMED) {
            int currentCount = participationCounts.getOrDefault(residentId, 0);
            participationCounts.put(residentId, currentCount + 1);
        }

        if (status == ParticipationStatus.DECLINED) {
            int currentCount = declinedCounts.getOrDefault(residentId, 0);
            declinedCounts.put(residentId, currentCount + 1);
        }

        supportLog.add(
                "Recorded status " + status + " for resident " + residentId
        );
    }

    public void updateHistoryFromProposal(EventProposal proposal) {
        if (proposal == null) {
            return;
        }

        for (Map.Entry<String, ParticipationStatus> entry
                : proposal.getParticipantStatuses().entrySet()) {

            String residentId = entry.getKey();
            ParticipationStatus status = entry.getValue();

            recordParticipationStatus(residentId, status);
        }
    }

    public int getParticipationCount(String residentId) {
        return participationCounts.getOrDefault(residentId, 0);
    }

    public int getDeclinedCount(String residentId) {
        return declinedCounts.getOrDefault(residentId, 0);
    }

    public boolean isLessActiveResident(String residentId) {
        return getParticipationCount(residentId) <= 1;
    }

    private void logSuggestionResult(Resident resident, List<Activity> suggestions) {
        if (suggestions.isEmpty()) {
            supportLog.add(
                    "No activity suggestions found for resident " + resident.getName()
            );
            return;
        }

        supportLog.add(
                "Generated " + suggestions.size()
                        + " suggestion(s) for resident "
                        + resident.getName()
        );
    }
    // communication with ScenarioAgent
    private void addScenarioSuggestionRequestReceiver() {
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION),
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
                );

                ACLMessage message = myAgent.receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleScenarioSuggestionRequest(message);
            }
        });
    }
    private void handleScenarioSuggestionRequest(ACLMessage message) {
        String content = message.getContent();

        System.out.println(
                getLocalName() + " <- " + message.getSender().getLocalName()
                        + ": suggestion request: " + content
        );

        String replyContent;

        try {
            EventProposal proposal = parseProposalRequest(content);

            suggestResidentsForProposal(proposal, knownResidents);

            replyContent = buildSuggestedResidentsResponse(proposal);

        } catch (Exception ex) {
            System.err.println(getLocalName() + " failed to process suggestion request:");
            ex.printStackTrace();

            replyContent = "UNKNOWN|-";
        }

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(SCENARIO_SOCIAL_SUGGESTIONS_CONVERSATION);
        reply.setContent(replyContent);

        send(reply);

        System.out.println(
                getLocalName() + " -> " + message.getSender().getLocalName()
                        + ": suggested residents: " + replyContent
        );
    }
    private EventProposal parseProposalRequest(String content) {
        String[] parts = content.split("\\|");

        if (parts.length < 6) {
            throw new IllegalArgumentException(
                    "Wrong social suggestion request format. Expected: proposalId|activityId|activityName|activityType|maxParticipants|requiredMobilityLevel"
            );
        }

        String proposalId = parts[0];

        Activity activity = new Activity(
                parts[1],
                parts[2],
                ActivityType.valueOf(parts[3]),
                Integer.parseInt(parts[4]),
                MobilityLevel.valueOf(parts[5])
        );

        return new EventProposal(
                proposalId,
                null,
                activity,
                null
        );
    }
    private String buildSuggestedResidentsResponse(EventProposal proposal) {
        if (proposal.getParticipantStatuses().isEmpty()) {
            return proposal.getId() + "|-";
        }

        String residentIds = proposal.getParticipantStatuses()
                .keySet()
                .stream()
                .collect(Collectors.joining(","));

        return proposal.getId() + "|" + residentIds;
    }


}
