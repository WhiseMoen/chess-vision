package ru.chessvision.core;

import java.util.Set;

public record PieceInsight(
        Square square,
        Piece piece,
        Set<Square> vision,
        Set<Square> moves,
        int attackers,
        int defenders,
        int potential,
        String summary
) {}
