package org.example.model;

public class ResourceBooking {
    private String resourceId;
    private int quantity;
    private TimeSlot timeSlot;

    public ResourceBooking(String resourceId, int quantity, TimeSlot timeSlot) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Booked quantity must be positive.");
        }

        this.resourceId = resourceId;
        this.quantity = quantity;
        this.timeSlot = timeSlot;
    }

    public String getResourceId() {
        return resourceId;
    }

    public int getQuantity() {
        return quantity;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public boolean overlapsWith(TimeSlot otherTimeSlot) {
        return timeSlot.overlapsWith(otherTimeSlot);
    }

    @Override
    public String toString() {
        return "ResourceBooking{" +
                "resourceId='" + resourceId + '\'' +
                ", quantity=" + quantity +
                ", timeSlot=" + timeSlot +
                '}';
    }
}
