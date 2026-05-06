package org.example.model;

import java.util.List;

public class HealthProfile {
    private MobilityLevel mobilityLevel;
    private List<String> limitations;

    public HealthProfile(MobilityLevel mobilityLevel, List<String> limitations)
    {
        this.mobilityLevel = mobilityLevel;
        this.limitations = limitations;
    }

    public MobilityLevel getMobilityLevel(){
        return this.mobilityLevel;
    }

    public List<String> getLimitations(){
        return limitations;
    }

    @Override
    public String toString(){
        return "HealthProfile{mobilitylevel=" + mobilityLevel + ", limitations=" + limitations + "}";
    }

}
