package ru.chessvision.core;

public record Pattern(Severity severity, String title, String explanation, Square square) {
    public enum Severity { DANGER, WARNING, OPPORTUNITY, INFO }
}
