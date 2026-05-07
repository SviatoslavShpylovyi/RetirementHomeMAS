package org.example.model;

public class Resource {
    private String id;
    private String name;
    private int totalQuantity;

    public Resource(String id, String name, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Resource quantity cannot be negative.");
        }

        this.id = id;
        this.name = name;
        this.totalQuantity = totalQuantity;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", totalQuantity=" + totalQuantity +
                '}';
    }
}
