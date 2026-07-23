package ru.chessvision.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class PatternAnalyzerTest {
    private final PatternAnalyzer analyzer = new PatternAnalyzer();

    @Test public void bishopVisionStopsAtFirstPiece() {
        Board board = Fen.parse("8/8/5p2/8/3B4/2P5/8/8 w - - 0 1");
        PieceInsight bishop = analyzer.analyze(board).pieces().get(Square.fromAlgebraic("d4"));
        assertTrue(bishop.vision().contains(Square.fromAlgebraic("f6")));
        assertFalse(bishop.vision().contains(Square.fromAlgebraic("g7")));
        assertTrue(bishop.vision().contains(Square.fromAlgebraic("c3")));
        assertFalse(bishop.moves().contains(Square.fromAlgebraic("c3")));
    }

    @Test public void knightSeesThroughBlockers() {
        Board board = Fen.parse("8/8/8/3P1P2/2P1N1P1/3P1P2/8/8 w - - 0 1");
        PieceInsight knight = analyzer.analyze(board).pieces().get(Square.fromAlgebraic("e4"));
        assertEquals(8, knight.vision().size());
    }

    @Test public void detectsUnderDefendedPiece() {
        Board board = Fen.parse("4k3/8/8/8/8/2b5/3R4/7K w - - 0 1");
        Analysis result = analyzer.analyze(board);
        PieceInsight rook = result.pieces().get(Square.fromAlgebraic("d2"));
        assertEquals(1, rook.attackers());
        assertEquals(0, rook.defenders());
        assertTrue(result.patterns().stream().anyMatch(p ->
                p.severity() == Pattern.Severity.DANGER && Square.fromAlgebraic("d2").equals(p.square())));
    }

    @Test public void parsesInitialPosition() {
        Board board = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        assertEquals(32, board.pieces().size());
        assertEquals(Piece.Type.KING, board.get(Square.fromAlgebraic("e1")).type());
    }
}
