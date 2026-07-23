package ru.chessvision.game;

import java.util.*;

import ru.chessvision.core.*;

public final class PatternBot {
    public record Move(Square from, Square to, String reason) {}

    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private final Random random = new Random();

    public Move choose(Board board) {
        Piece.Color side = board.sideToMove();
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (var entry : board.pieces().entrySet()) {
            if (entry.getValue().color() != side) continue;
            for (Square to : analyzer.legalMoves(board, entry.getKey())) {
                Piece captured = board.get(to);
                if (captured != null && captured.type() == Piece.Type.KING) continue;
                Board next = board.copy();
                next.move(entry.getKey(), to);
                Analysis position = analyzer.analyze(next);
                PieceInsight moved = position.pieces().get(to);
                int capture = captured == null ? 0 : captured.type().value * 120;
                int safety = moved == null ? 0 : (moved.attackers() > moved.defenders() ? -entry.getValue().type().value * 70 : 40);
                int potential = moved == null ? 0 : moved.potential() * 2;
                int influence = side == Piece.Color.WHITE
                        ? position.whiteInfluence() - position.blackInfluence()
                        : position.blackInfluence() - position.whiteInfluence();
                int score = capture + safety + potential + influence * 3 + random.nextInt(12);
                if (score > bestScore) {
                    bestScore = score;
                    String reason = captured != null ? "выигрываю материал на " + to.algebraic()
                            : moved != null && moved.potential() >= 65 ? "ставлю фигуру на активное поле"
                            : "улучшаю влияние и мобильность";
                    best = new Move(entry.getKey(), to, reason);
                }
            }
        }
        return best;
    }
}
