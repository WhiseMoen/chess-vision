package ru.chessvision.core;

public record Piece(Color color, Type type) {
    public enum Color {
        WHITE, BLACK;
        public Color opposite() { return this == WHITE ? BLACK : WHITE; }
    }

    public enum Type {
        KING(0, "Король"), QUEEN(9, "Ферзь"), ROOK(5, "Ладья"),
        BISHOP(3, "Слон"), KNIGHT(3, "Конь"), PAWN(1, "Пешка");

        public final int value;
        public final String russianName;

        Type(int value, String russianName) {
            this.value = value;
            this.russianName = russianName;
        }
    }
}
