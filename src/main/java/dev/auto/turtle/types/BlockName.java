package dev.auto.turtle.types;

public record BlockName(String name, String namespace) {
    public String getIdentifier() {
        return namespace + ":" + name;
    }
}
