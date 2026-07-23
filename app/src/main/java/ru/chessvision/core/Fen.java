package ru.chessvision.core;

public final class Fen {
    private Fen() {}

    public static Board parse(String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length < 2) throw new IllegalArgumentException("FEN must include side to move");
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN must contain 8 ranks");
        Board board = new Board();
        for (int row = 0; row < 8; row++) {
            int file = 0;
            for (char c : ranks[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    if (file > 7) throw new IllegalArgumentException("Rank too long");
                    Piece.Color color = Character.isUpperCase(c) ? Piece.Color.WHITE : Piece.Color.BLACK;
                    Piece.Type type = switch (Character.toLowerCase(c)) {
                        case 'k' -> Piece.Type.KING;
                        case 'q' -> Piece.Type.QUEEN;
                        case 'r' -> Piece.Type.ROOK;
                        case 'b' -> Piece.Type.BISHOP;
                        case 'n' -> Piece.Type.KNIGHT;
                        case 'p' -> Piece.Type.PAWN;
                        default -> throw new IllegalArgumentException("Unknown piece: " + c);
                    };
                    board.set(new Square(file++, 7 - row), new Piece(color, type));
                }
            }
            if (file != 8) throw new IllegalArgumentException("Rank must contain 8 squares");
        }
        board.setSideToMove(fields[1].equals("b") ? Piece.Color.BLACK : Piece.Color.WHITE);
        return board;
    }
}
