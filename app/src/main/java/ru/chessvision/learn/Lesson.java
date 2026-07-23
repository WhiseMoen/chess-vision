package ru.chessvision.learn;

public record Lesson(
        String course,
        String title,
        String fen,
        String idea,
        String question,
        String hint,
        String answer,
        String expectedFrom,
        String expectedTo
) {
    public boolean interactive() {
        return expectedFrom != null && expectedTo != null;
    }
}
