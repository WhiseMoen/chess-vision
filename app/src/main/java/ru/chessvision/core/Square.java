package ru.chessvision.core;

public record Square(int file, int rank) {
    public Square {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Square outside board");
        }
    }

    public static Square fromAlgebraic(String value) {
        if (value == null || value.length() != 2) throw new IllegalArgumentException("Bad square");
        return new Square(value.charAt(0) - 'a', value.charAt(1) - '1');
    }

    public String algebraic() {
        return "" + (char) ('a' + file) + (char) ('1' + rank);
    }
}
