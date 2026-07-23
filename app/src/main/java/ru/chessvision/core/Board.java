package ru.chessvision.core;

import java.util.*;

public final class Board {
    private final Piece[][] cells = new Piece[8][8];
    private Piece.Color sideToMove = Piece.Color.WHITE;

    public Piece get(Square square) { return cells[square.file()][square.rank()]; }
    public void set(Square square, Piece piece) { cells[square.file()][square.rank()] = piece; }
    public Piece.Color sideToMove() { return sideToMove; }
    public void setSideToMove(Piece.Color side) { sideToMove = side; }

    public Map<Square, Piece> pieces() {
        Map<Square, Piece> result = new LinkedHashMap<>();
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                if (cells[file][rank] != null) result.put(new Square(file, rank), cells[file][rank]);
            }
        }
        return result;
    }

    public Board copy() {
        Board board = new Board();
        board.sideToMove = sideToMove;
        for (int f = 0; f < 8; f++) System.arraycopy(cells[f], 0, board.cells[f], 0, 8);
        return board;
    }

    public void move(Square from, Square to) {
        Piece piece = get(from);
        if (piece == null) return;
        set(to, piece);
        set(from, null);
        sideToMove = sideToMove.opposite();
    }
}
