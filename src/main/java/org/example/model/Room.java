package org.example.model;

public class Room {
    private String id;
    private String name;
    private int capacity;
    private boolean accessible;


    public Room(String id,String name, int capacity,  boolean accessible) {
        this.id = id;
        this.accessible = accessible;
        this.capacity = capacity;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Room{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", capacity=" + capacity +
                ", accessible=" + accessible +
                '}';
    }
}
