package ru.chessvision.learn;

import org.junit.Test;
import ru.chessvision.core.*;

import static org.junit.Assert.*;

public class LessonCatalogTest {
    private final PatternAnalyzer analyzer = new PatternAnalyzer();

    @Test public void practiceContainsManyIndependentPositions() {
        long practice = LessonCatalog.all().stream().filter(Lesson::interactive).count();
        assertTrue(practice >= 10);
    }

    @Test public void everyExpectedMoveIsLegalInItsPosition() {
        for (Lesson lesson : LessonCatalog.all()) {
            if (!lesson.interactive()) continue;
            Board board = Fen.parse(lesson.fen());
            Square from = Square.fromAlgebraic(lesson.expectedFrom());
            Square to = Square.fromAlgebraic(lesson.expectedTo());
            assertNotNull(lesson.title(), board.get(from));
            assertTrue(lesson.title() + " " + from + " -> " + to,
                    analyzer.legalMoves(board, from).contains(to));
        }
    }
}
