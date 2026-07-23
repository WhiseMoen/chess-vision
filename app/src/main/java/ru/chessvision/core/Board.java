package ru.chessvision.core;

import java.util.*;

public final class Board {
    private final Piece[][] cells = new Piece[8][8];
    private Piece.Color sideToMove = Piece.Color.WHITE;
    private String castlingRights = "-";
    private String enPassant = "-";

    public Piece get(Square square) { return cells[square.file()][square.rank()]; }
    public void set(Square square, Piece piece) { cells[square.file()][square.rank()] = piece; }
    public Piece.Color sideToMove() { return sideToMove; }
    public void setSideToMove(Piece.Color side) { sideToMove = side; }
    public String castlingRights() { return castlingRights; }
    public void setCastlingRights(String rights) { castlingRights = rights == null || rights.isBlank() ? "-" : rights; }
    public String enPassant() { return enPassant; }
    public void setEnPassant(String square) { enPassant = square == null || square.isBlank() ? "-" : square; }

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
        board.castlingRights = castlingRights;
        board.enPassant = enPassant;
        for (int f = 0; f < 8; f++) System.arraycopy(cells[f], 0, board.cells[f], 0, 8);
        return board;
    }

    public void move(Square from, Square to) {
        Piece piece = get(from);
        if (piece == null) return;
        Piece captured = get(to);
        String previousEnPassant = enPassant;
        updateCastling(piece, from, to, captured);
        enPassant = "-";
        if (piece.type() == Piece.Type.PAWN && captured == null
                && from.file() != to.file() && to.algebraic().equals(previousEnPassant)) {
            set(new Square(to.file(), from.rank()), null);
        }
        if (piece.type() == Piece.Type.PAWN && Math.abs(to.rank() - from.rank()) == 2) {
            enPassant = new Square(from.file(), (from.rank() + to.rank()) / 2).algebraic();
        }
        set(to, piece);
        set(from, null);
        if (piece.type() == Piece.Type.KING && Math.abs(to.file() - from.file()) == 2) {
            int rookFrom = to.file() > from.file() ? 7 : 0;
            int rookTo = to.file() > from.file() ? 5 : 3;
            Square rookSquare = new Square(rookFrom, from.rank());
            set(new Square(rookTo, from.rank()), get(rookSquare));
            set(rookSquare, null);
        }
        if (piece.type() == Piece.Type.PAWN && (to.rank() == 0 || to.rank() == 7)) {
            set(to, new Piece(piece.color(), Piece.Type.QUEEN));
        }
        sideToMove = sideToMove.opposite();
    }

    private void updateCastling(Piece piece, Square from, Square to, Piece captured) {
        if (piece.type() == Piece.Type.KING) {
            castlingRights = remove(piece.color() == Piece.Color.WHITE ? "KQ" : "kq");
        }
        if (piece.type() == Piece.Type.ROOK) castlingRights = remove(rightForRookSquare(from));
        if (captured != null && captured.type() == Piece.Type.ROOK) castlingRights = remove(rightForRookSquare(to));
        if (castlingRights.isEmpty()) castlingRights = "-";
    }

    private String rightForRookSquare(Square square) {
        if (square.equals(new Square(0, 0))) return "Q";
        if (square.equals(new Square(7, 0))) return "K";
        if (square.equals(new Square(0, 7))) return "q";
        if (square.equals(new Square(7, 7))) return "k";
        return "";
    }

    private String remove(String values) {
        String result = castlingRights.equals("-") ? "" : castlingRights;
        for (char value : values.toCharArray()) result = result.replace(String.valueOf(value), "");
        return result;
    }
}
