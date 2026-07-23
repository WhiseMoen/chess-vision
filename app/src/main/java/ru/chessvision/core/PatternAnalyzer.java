package ru.chessvision.core;

import java.util.*;

import static ru.chessvision.core.Piece.Color;
import static ru.chessvision.core.Piece.Type;

public final class PatternAnalyzer {
    private static final int[][] KNIGHT = {{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
    private static final int[][] KING = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    private static final int[][] ROOK = {{1,0},{-1,0},{0,1},{0,-1}};
    private static final int[][] BISHOP = {{1,1},{1,-1},{-1,1},{-1,-1}};

    public Analysis analyze(Board board) {
        Map<Color, Map<Square, Integer>> attacks = new EnumMap<>(Color.class);
        attacks.put(Color.WHITE, attackCounts(board, Color.WHITE));
        attacks.put(Color.BLACK, attackCounts(board, Color.BLACK));

        Map<Square, PieceInsight> insights = new LinkedHashMap<>();
        List<Pattern> patterns = new ArrayList<>();
        List<VisualCue> visualCues = new ArrayList<>();
        int whiteInfluence = influence(attacks.get(Color.WHITE));
        int blackInfluence = influence(attacks.get(Color.BLACK));

        for (var entry : board.pieces().entrySet()) {
            Square square = entry.getKey();
            Piece piece = entry.getValue();
            Set<Square> vision = vision(board, square);
            Set<Square> moves = pseudoMoves(board, square);
            int attackers = attacks.get(piece.color().opposite()).getOrDefault(square, 0);
            int defenders = attacks.get(piece.color()).getOrDefault(square, 0);
            int mobilityIdeal = switch (piece.type()) {
                case QUEEN -> 12; case ROOK -> 7; case BISHOP -> 6; case KNIGHT -> 5;
                case KING -> 3; case PAWN -> 2;
            };
            int mobilityScore = Math.min(40, moves.size() * 40 / Math.max(1, mobilityIdeal));
            int safetyScore = attackers == 0 ? 35 : defenders >= attackers ? 20 : 0;
            int activityScore = Math.min(25, centralVision(vision) * 5);
            int potential = Math.min(100, mobilityScore + safetyScore + activityScore);
            String summary = summary(piece, moves.size(), attackers, defenders, potential);
            insights.put(square, new PieceInsight(square, piece, vision, moves, attackers, defenders, potential, summary));

            if (attackers > defenders && piece.type() != Type.KING) {
                for (var attacker : board.pieces().entrySet()) {
                    if (attacker.getValue().color() == piece.color().opposite()
                            && vision(board, attacker.getKey()).contains(square)) {
                        visualCues.add(new VisualCue(VisualCue.Type.PRESSURE, attacker.getKey(), square,
                                "давление", piece.type().value + 2));
                    }
                }
                patterns.add(new Pattern(Pattern.Severity.DANGER,
                        piece.type().russianName + " " + square.algebraic() + " под угрозой",
                        "Атак: " + attackers + ", защит: " + defenders + ". Фигура может быть потеряна — найдите уход, защиту или встречную угрозу.",
                        square));
            } else if (attackers > 0) {
                patterns.add(new Pattern(Pattern.Severity.WARNING,
                        "Напряжение вокруг " + square.algebraic(),
                        piece.type().russianName + " атакован, но пока достаточно защищён (" + defenders + " против " + attackers + ").",
                        square));
            }

            if (moves.isEmpty() && piece.type() != Type.PAWN && piece.type() != Type.KING) {
                patterns.add(new Pattern(Pattern.Severity.WARNING,
                        piece.type().russianName + " заперт на " + square.algebraic(),
                        "У фигуры нет доступных полей. Освободите линию или перестройте пешечную цепь.",
                        square));
            } else if (potential <= 25 && piece.type().value >= 3) {
                patterns.add(new Pattern(Pattern.Severity.INFO,
                        "Низкий потенциал: " + square.algebraic(),
                        "Фигура использует мало направлений и почти не влияет на центр. Ищите активное поле, а не одиночный ход.",
                        square));
            }

            if (potential >= 70 && piece.type() != Type.KING) {
                visualCues.add(new VisualCue(VisualCue.Type.STRENGTH, square, square,
                        "сила " + potential, potential));
            } else if (potential <= 25 && piece.type().value >= 3) {
                visualCues.add(new VisualCue(VisualCue.Type.WEAKNESS, square, square,
                        "слабость " + potential, 100 - potential));
            }
        }

        addLoosePieces(board, attacks, patterns);
        addCentralControl(attacks, patterns);
        addSynergiesAndForks(board, visualCues, patterns);
        patterns.sort(Comparator.comparingInt(p -> p.severity().ordinal()));
        if (patterns.isEmpty()) {
            patterns.add(new Pattern(Pattern.Severity.INFO, "Позиция устойчива",
                    "Явных слабых фигур нет. Сравните худшую фигуру каждой стороны и улучшите её потенциал.", null));
        }
        visualCues.sort(Comparator.comparingInt(VisualCue::weight).reversed());
        return new Analysis(insights, patterns, visualCues, whiteInfluence, blackInfluence);
    }

    public Set<Square> vision(Board board, Square from) {
        Piece piece = board.get(from);
        if (piece == null) return Set.of();
        LinkedHashSet<Square> result = new LinkedHashSet<>();
        switch (piece.type()) {
            case KNIGHT -> addJumps(board, from, piece, KNIGHT, result, true);
            case KING -> addJumps(board, from, piece, KING, result, true);
            case ROOK -> addRays(board, from, ROOK, result);
            case BISHOP -> addRays(board, from, BISHOP, result);
            case QUEEN -> { addRays(board, from, ROOK, result); addRays(board, from, BISHOP, result); }
            case PAWN -> {
                int direction = piece.color() == Color.WHITE ? 1 : -1;
                addIfInside(result, from.file() - 1, from.rank() + direction);
                addIfInside(result, from.file() + 1, from.rank() + direction);
            }
        }
        return result;
    }

    public Set<Square> pseudoMoves(Board board, Square from) {
        Piece piece = board.get(from);
        if (piece == null) return Set.of();
        LinkedHashSet<Square> result = new LinkedHashSet<>();
        if (piece.type() == Type.PAWN) {
            int direction = piece.color() == Color.WHITE ? 1 : -1;
            int nextRank = from.rank() + direction;
            if (inside(from.file(), nextRank)) {
                Square one = new Square(from.file(), nextRank);
                if (board.get(one) == null) {
                    result.add(one);
                    int home = piece.color() == Color.WHITE ? 1 : 6;
                    int twoRank = from.rank() + direction * 2;
                    Square two = new Square(from.file(), twoRank);
                    if (from.rank() == home && board.get(two) == null) result.add(two);
                }
            }
            for (Square target : vision(board, from)) {
                Piece occupant = board.get(target);
                if (occupant != null && occupant.color() != piece.color()) result.add(target);
            }
            return result;
        }
        for (Square target : vision(board, from)) {
            Piece occupant = board.get(target);
            if (occupant == null || occupant.color() != piece.color()) result.add(target);
        }
        return result;
    }

    private Map<Square, Integer> attackCounts(Board board, Color color) {
        Map<Square, Integer> result = new HashMap<>();
        for (var entry : board.pieces().entrySet()) {
            if (entry.getValue().color() != color) continue;
            for (Square target : vision(board, entry.getKey())) result.merge(target, 1, Integer::sum);
        }
        return result;
    }

    private void addLoosePieces(Board board, Map<Color, Map<Square, Integer>> attacks, List<Pattern> patterns) {
        for (var entry : board.pieces().entrySet()) {
            Piece piece = entry.getValue();
            if (piece.type() == Type.KING || piece.type() == Type.PAWN) continue;
            int defenders = attacks.get(piece.color()).getOrDefault(entry.getKey(), 0);
            if (defenders == 0) {
                patterns.add(new Pattern(Pattern.Severity.OPPORTUNITY,
                        "Незащищённая фигура на " + entry.getKey().algebraic(),
                        piece.type().russianName + " не поддержан ни одной фигурой. Это тактическая зацепка, даже если сейчас атак нет.",
                        entry.getKey()));
            }
        }
    }

    private void addCentralControl(Map<Color, Map<Square, Integer>> attacks, List<Pattern> patterns) {
        Square[] center = {new Square(3,3), new Square(4,3), new Square(3,4), new Square(4,4)};
        int white = 0, black = 0;
        for (Square square : center) {
            white += attacks.get(Color.WHITE).getOrDefault(square, 0);
            black += attacks.get(Color.BLACK).getOrDefault(square, 0);
        }
        if (Math.abs(white - black) >= 3) {
            Color leader = white > black ? Color.WHITE : Color.BLACK;
            patterns.add(new Pattern(Pattern.Severity.OPPORTUNITY,
                    (leader == Color.WHITE ? "Белые" : "Чёрные") + " контролируют центр",
                    "Давление на четыре центральных поля: " + Math.max(white, black) + " против " + Math.min(white, black) +
                            ". Это даёт фигурам больше маршрутов и пространства.", null));
        }
    }

    private void addSynergiesAndForks(Board board, List<VisualCue> cues, List<Pattern> patterns) {
        for (var entry : board.pieces().entrySet()) {
            Square from = entry.getKey();
            Piece source = entry.getValue();
            List<Square> valuableTargets = new ArrayList<>();
            for (Square target : vision(board, from)) {
                Piece targetPiece = board.get(target);
                if (targetPiece == null) continue;
                if (targetPiece.color() == source.color()) {
                    if (!from.equals(target) && targetPiece.type() != Type.KING) {
                        cues.add(new VisualCue(VisualCue.Type.SYNERGY, from, target,
                                "защита", Math.max(1, targetPiece.type().value)));
                    }
                } else if (targetPiece.type().value >= 3 || targetPiece.type() == Type.KING) {
                    valuableTargets.add(target);
                }
            }
            if (valuableTargets.size() >= 2) {
                int weight = valuableTargets.stream()
                        .map(board::get)
                        .mapToInt(p -> p.type() == Type.KING ? 10 : p.type().value)
                        .sum();
                for (Square target : valuableTargets) {
                    cues.add(new VisualCue(VisualCue.Type.FORK, from, target, "вилка", weight));
                }
                String targets = valuableTargets.stream().limit(3)
                        .map(Square::algebraic)
                        .reduce((a, b) -> a + " и " + b).orElse("");
                patterns.add(new Pattern(Pattern.Severity.OPPORTUNITY,
                        "Двойное нападение с " + from.algebraic(),
                        source.type().russianName + " одновременно смотрит на ценные цели " + targets +
                                ". Проверьте, могут ли обе уйти или защититься одним темпом.", from));
            }
        }
    }

    private void addRays(Board board, Square from, int[][] directions, Set<Square> out) {
        for (int[] direction : directions) {
            int file = from.file() + direction[0], rank = from.rank() + direction[1];
            while (inside(file, rank)) {
                Square target = new Square(file, rank);
                out.add(target);
                if (board.get(target) != null) break;
                file += direction[0];
                rank += direction[1];
            }
        }
    }

    private void addJumps(Board board, Square from, Piece piece, int[][] offsets, Set<Square> out, boolean includeOwn) {
        for (int[] offset : offsets) {
            int file = from.file() + offset[0], rank = from.rank() + offset[1];
            if (!inside(file, rank)) continue;
            Square target = new Square(file, rank);
            if (includeOwn || board.get(target) == null || board.get(target).color() != piece.color()) out.add(target);
        }
    }

    private int influence(Map<Square, Integer> attacks) {
        int result = 0;
        for (var entry : attacks.entrySet()) {
            int centerBonus = entry.getKey().file() >= 2 && entry.getKey().file() <= 5 &&
                    entry.getKey().rank() >= 2 && entry.getKey().rank() <= 5 ? 2 : 1;
            result += Math.min(2, entry.getValue()) * centerBonus;
        }
        return result;
    }

    private int centralVision(Set<Square> vision) {
        int result = 0;
        for (Square square : vision) {
            if (square.file() >= 2 && square.file() <= 5 && square.rank() >= 2 && square.rank() <= 5) result++;
        }
        return result;
    }

    private String summary(Piece piece, int moves, int attackers, int defenders, int potential) {
        String state = potential >= 70 ? "активна" : potential >= 40 ? "может быть улучшена" : "ограничена";
        String danger = attackers == 0 ? "в безопасности" :
                defenders >= attackers ? "в напряжении, но защищена" : "под реальной угрозой";
        return piece.type().russianName + " " + state + ": " + moves + " доступных полей, " + danger + ".";
    }

    private void addIfInside(Set<Square> result, int file, int rank) {
        if (inside(file, rank)) result.add(new Square(file, rank));
    }

    private boolean inside(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }
}
