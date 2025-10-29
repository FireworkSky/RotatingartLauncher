// ControlLayout.java
package com.app.ralaunch.model;

import java.util.ArrayList;
import java.util.List;

public class ControlLayout {
    private String name;
    private List<ControlElement> elements;

    public ControlLayout(String name) {
        this.name = name;
        this.elements = new ArrayList<>();
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ControlElement> getElements() { return elements; }
    public void setElements(List<ControlElement> elements) { this.elements = elements; }

    public void addElement(ControlElement element) {
        elements.add(element);
    }

    public void removeElement(ControlElement element) {
        elements.remove(element);
    }
}