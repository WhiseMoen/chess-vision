package ru.chessvision.game;

import org.junit.Test;
import ru.chessvision.core.*;

import static org.junit.Assert.*;

public class OpeningCatalogTest {
    @Test public void everyOpeningBuildsAPlayableWhitePosition() {
        for (Opening opening : OpeningCatalog.all()) {
            Board board = opening.position();
            assertEquals(opening.name(), Piece.Color.WHITE, board.sideToMove());
            assertTrue(opening.name(), board.pieces().size() >= 30);
        }
    }

    @Test public void patternBotReturnsMoveForOpening() {
        Board board = OpeningCatalog.all().get(0).position();
        board.setSideToMove(Piece.Color.BLACK);
        PatternBot.Move move = new PatternBot().choose(board);
        assertNotNull(move);
        assertEquals(Piece.Color.BLACK, board.get(move.from()).color());
    }

    @Test public void everyCatalogMoveIsLegal() {
        PatternAnalyzer analyzer = new PatternAnalyzer();
        for (Opening opening : OpeningCatalog.all()) {
            Board board = Fen.parse(ChessPositions.START_FEN);
            for (String move : opening.moves()) {
                Square from = Square.fromAlgebraic(move.substring(0, 2));
                Square to = Square.fromAlgebraic(move.substring(2, 4));
                assertTrue(opening.toString() + " " + move,
                        analyzer.legalMoves(board, from).contains(to));
                board.move(from, to);
            }
        }
    }
}
