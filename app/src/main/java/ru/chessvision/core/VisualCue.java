package ru.chessvision.core;

public record VisualCue(Type type, Square from, Square to, String label, int weight) {
    public enum Type {
        FORK,
        SYNERGY,
        PRESSURE,
        STRENGTH,
        WEAKNESS
    }
}
