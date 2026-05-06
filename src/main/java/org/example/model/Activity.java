package org.example.model;

public class Activity {
    private String id;
    private String name;
    private ActivityType type;
    private int maxParticipants;
    private MobilityLevel requiredMobilityLevel;

    public Activity(String id,  String name, ActivityType type, int maxParticipants,MobilityLevel requiredMobilityLevel) {
        this.id = id;
        this.requiredMobilityLevel = requiredMobilityLevel;
        this.type = type;
        this.name = name;
        this.maxParticipants = maxParticipants;
    }


    public String getId() {
        return id;
    }

    public MobilityLevel getRequiredMobilityLevel() {
        return requiredMobilityLevel;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public String getName() {
        return name;
    }

    public ActivityType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Activity{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", maxParticipants=" + maxParticipants +
                ", requiredMobilityLevel=" + requiredMobilityLevel +
                '}';
    }
}
