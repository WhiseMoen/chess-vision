package ru.chessvision.game;

import org.junit.Test;
import ru.chessvision.core.*;

import static org.junit.Assert.*;

public class OpeningCatalogTest {
    @Test public void everyOpeningBuildsAPlayableWhitePosition() {
        for (Opening opening : OpeningCatalog.all()) {
            Board board = opening.position();
            assertEquals(opening.name(), Piece.Color.WHITE, board.sideToMove());
            assertTrue(opening.name(), board.pieces().size() >= 31);
        }
    }

    @Test public void patternBotReturnsMoveForOpening() {
        Board board = OpeningCatalog.all().get(0).position();
        board.setSideToMove(Piece.Color.BLACK);
        PatternBot.Move move = new PatternBot().choose(board);
        assertNotNull(move);
        assertEquals(Piece.Color.BLACK, board.get(move.from()).color());
    }
}
