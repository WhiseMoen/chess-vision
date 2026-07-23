package ru.chessvision.game;

import ru.chessvision.core.*;

public record Opening(String name, String family, String variation, String idea, String[] moves) {

    public Board position() {
        Board board = Fen.parse(ChessPositions.START_FEN);
        for (String move : moves) {
            board.move(Square.fromAlgebraic(move.substring(0, 2)),
                    Square.fromAlgebraic(move.substring(2, 4)));
        }
        return board;
    }

    public String line() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < moves.length; i += 2) {
            if (result.length() > 0) result.append("  ");
            result.append(i / 2 + 1).append(". ").append(pretty(moves[i]));
            if (i + 1 < moves.length) result.append(" ").append(pretty(moves[i + 1]));
        }
        return result.toString();
    }

    private String pretty(String move) {
        return move.substring(0, 2) + "–" + move.substring(2, 4);
    }

    @Override public String toString() {
        return variation == null || variation.isBlank() ? name : name + " · " + variation;
    }
}
