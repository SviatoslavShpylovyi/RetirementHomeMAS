package org.example.model;

import java.time.LocalDateTime;

public class TimeSlot {
    private LocalDateTime startTime;
    private LocalDateTime endTime;


    public TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        if(startTime ==null  || endTime == null){
            throw new IllegalArgumentException("Start Time and End Time Cannot Be NULL!");
        }
        if(!startTime.isBefore((endTime)))
        {
            throw new IllegalArgumentException("Start Time Cannot Be After End Time!");
        }

        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean overlapsWith(TimeSlot other)
    {
        return this.startTime.isBefore((other.endTime)) && this.endTime.isAfter((other.startTime));
    }


    @Override
    public String toString() {
        return "TimeSlot{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
