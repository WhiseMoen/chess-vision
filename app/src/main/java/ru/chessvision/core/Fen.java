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
        if (fields.length > 2) board.setCastlingRights(fields[2]);
        if (fields.length > 3) board.setEnPassant(fields[3]);
        return board;
    }

    public static String write(Board board) {
        StringBuilder result = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = board.get(new Square(file, rank));
                if (piece == null) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    result.append(empty);
                    empty = 0;
                }
                char symbol = switch (piece.type()) {
                    case KING -> 'k'; case QUEEN -> 'q'; case ROOK -> 'r';
                    case BISHOP -> 'b'; case KNIGHT -> 'n'; case PAWN -> 'p';
                };
                result.append(piece.color() == Piece.Color.WHITE ? Character.toUpperCase(symbol) : symbol);
            }
            if (empty > 0) result.append(empty);
            if (rank > 0) result.append('/');
        }
        result.append(board.sideToMove() == Piece.Color.WHITE ? " w " : " b ");
        result.append(board.castlingRights()).append(' ').append(board.enPassant()).append(" 0 1");
        return result.toString();
    }
}
