package org.example.model;

import java.util.List;

public class Resident {
    private String id;
    private String name;
    private List<ActivityType> preferences;
    private HealthProfile healthProfile;
    private boolean willingToParticipate;


    public Resident(boolean willingToParticipate, HealthProfile healthProfile, List<ActivityType> preferences, String id, String name) {
        this.willingToParticipate = willingToParticipate;
        this.healthProfile = healthProfile;
        this.preferences = preferences;
        this.id = id;
        this.name = name;
    }


    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<ActivityType> getPreferences() {
        return preferences;
    }

    public HealthProfile getHealthProfile() {
        return healthProfile;
    }

    public boolean isWillingToParticipate() {
        return willingToParticipate;
    }


    @Override
    public String toString() {
        return "Resident{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", preferences=" + preferences +
                ", healthProfile=" + healthProfile +
                ", willingToParticipate=" + willingToParticipate +
                '}';
    }
}
