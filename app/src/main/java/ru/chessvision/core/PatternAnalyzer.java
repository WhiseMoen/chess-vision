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
        addLinePatterns(board, patterns);
        addStructuralPatterns(board, attacks, patterns);
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
                else if (target.algebraic().equals(board.enPassant())) result.add(target);
            }
            return result;
        }
        for (Square target : vision(board, from)) {
            Piece occupant = board.get(target);
            if (occupant == null || occupant.color() != piece.color()) result.add(target);
        }
        return result;
    }

    public Set<Square> legalMoves(Board board, Square from) {
        Piece piece = board.get(from);
        if (piece == null) return Set.of();
        LinkedHashSet<Square> legal = new LinkedHashSet<>();
        for (Square to : pseudoMoves(board, from)) {
            Piece target = board.get(to);
            if (target != null && target.type() == Type.KING) continue;
            Board next = board.copy();
            next.move(from, to);
            if (!kingAttacked(next, piece.color())) legal.add(to);
        }
        if (piece.type() == Type.KING) addCastlingMoves(board, from, piece, legal);
        return legal;
    }

    public boolean kingAttacked(Board board, Color color) {
        Square king = null;
        for (var entry : board.pieces().entrySet()) {
            if (entry.getValue().color() == color && entry.getValue().type() == Type.KING) {
                king = entry.getKey();
                break;
            }
        }
        if (king == null) return false;
        for (var entry : board.pieces().entrySet()) {
            if (entry.getValue().color() == color.opposite() && vision(board, entry.getKey()).contains(king)) {
                return true;
            }
        }
        return false;
    }

    private void addCastlingMoves(Board board, Square from, Piece king, Set<Square> legal) {
        int homeRank = king.color() == Color.WHITE ? 0 : 7;
        if (from.file() != 4 || from.rank() != homeRank || kingAttacked(board, king.color())) return;
        String rights = board.castlingRights();
        char kingSide = king.color() == Color.WHITE ? 'K' : 'k';
        char queenSide = king.color() == Color.WHITE ? 'Q' : 'q';
        if (rights.indexOf(kingSide) >= 0
                && empty(board, 5, homeRank) && empty(board, 6, homeRank)
                && !attacked(board, new Square(5, homeRank), king.color().opposite())
                && !attacked(board, new Square(6, homeRank), king.color().opposite())) {
            legal.add(new Square(6, homeRank));
        }
        if (rights.indexOf(queenSide) >= 0
                && empty(board, 1, homeRank) && empty(board, 2, homeRank) && empty(board, 3, homeRank)
                && !attacked(board, new Square(3, homeRank), king.color().opposite())
                && !attacked(board, new Square(2, homeRank), king.color().opposite())) {
            legal.add(new Square(2, homeRank));
        }
    }

    private boolean empty(Board board, int file, int rank) {
        return board.get(new Square(file, rank)) == null;
    }

    private boolean attacked(Board board, Square target, Color attacker) {
        for (var entry : board.pieces().entrySet()) {
            if (entry.getValue().color() == attacker && vision(board, entry.getKey()).contains(target)) return true;
        }
        return false;
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

    private void addLinePatterns(Board board, List<Pattern> patterns) {
        Set<String> found = new HashSet<>();
        for (var entry : board.pieces().entrySet()) {
            Piece source = entry.getValue();
            int[][] directions = switch (source.type()) {
                case BISHOP -> BISHOP;
                case ROOK -> ROOK;
                case QUEEN -> new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
                default -> new int[0][0];
            };
            for (int[] direction : directions) {
                List<Square> occupied = occupiedOnRay(board, entry.getKey(), direction);
                if (occupied.size() < 2) continue;
                Square firstSquare = occupied.get(0), secondSquare = occupied.get(1);
                Piece first = board.get(firstSquare), second = board.get(secondSquare);
                if (first.color() != source.color() && second.color() != source.color()) {
                    if (second.type() == Type.KING && first.type() != Type.KING) {
                        addUnique(patterns, found, "pin-" + firstSquare.algebraic(),
                                new Pattern(Pattern.Severity.OPPORTUNITY,
                                        "Связка на " + firstSquare.algebraic(),
                                        source.type().russianName + " прижимает " + first.type().russianName.toLowerCase() +
                                                " к королю. Усиливайте давление на связанную фигуру.", firstSquare));
                    } else if (first.type() == Type.KING && second.type().value > 0) {
                        addUnique(patterns, found, "skewer-" + entry.getKey().algebraic() + secondSquare.algebraic(),
                                new Pattern(Pattern.Severity.OPPORTUNITY,
                                        "Сквозной удар по линии",
                                        "После ухода короля с " + firstSquare.algebraic() + " открывается " +
                                                second.type().russianName.toLowerCase() + " на " + secondSquare.algebraic() + ".", entry.getKey()));
                    } else {
                        addUnique(patterns, found, "xray-" + entry.getKey().algebraic() + secondSquare.algebraic(),
                                new Pattern(Pattern.Severity.INFO,
                                        "Рентген по линии",
                                        source.type().russianName + " уже направлен сквозь " + firstSquare.algebraic() +
                                                " на цель " + secondSquare.algebraic() + ". Удаление промежуточной фигуры откроет луч.", entry.getKey()));
                    }
                } else if (first.color() == source.color()
                        && sameLinePiece(first.type(), direction)
                        && second.color() != source.color()) {
                    addUnique(patterns, found, "battery-" + entry.getKey().algebraic() + firstSquare.algebraic(),
                            new Pattern(Pattern.Severity.OPPORTUNITY,
                                    "Батарея фигур",
                                    source.type().russianName + " и " + first.type().russianName.toLowerCase() +
                                            " усиливают одну линию к " + secondSquare.algebraic() + ".", firstSquare));
                }
            }
        }
    }

    private void addStructuralPatterns(Board board, Map<Color, Map<Square, Integer>> attacks, List<Pattern> patterns) {
        for (var entry : board.pieces().entrySet()) {
            Square square = entry.getKey();
            Piece piece = entry.getValue();
            if (piece.type() == Type.KNIGHT && square.rank() >= 3 && square.rank() <= 4) {
                boolean enemyPawnCanChallenge = false;
                int enemyDirection = piece.color() == Color.WHITE ? -1 : 1;
                int pawnRank = square.rank() - enemyDirection;
                for (int file : new int[]{square.file() - 1, square.file() + 1}) {
                    if (!inside(file, pawnRank)) continue;
                    Piece candidate = board.get(new Square(file, pawnRank));
                    if (candidate != null && candidate.color() != piece.color() && candidate.type() == Type.PAWN) {
                        enemyPawnCanChallenge = true;
                    }
                }
                int defenders = attacks.get(piece.color()).getOrDefault(square, 0);
                if (!enemyPawnCanChallenge && defenders > 0) {
                    patterns.add(new Pattern(Pattern.Severity.OPPORTUNITY,
                            "Устойчивый форпост на " + square.algebraic(),
                            "Конь защищён, и его нельзя прогнать пешкой. Это долговременная сила позиции.", square));
                }
            }
            if (piece.type() == Type.PAWN) {
                int behind = piece.color() == Color.WHITE ? square.rank() - 1 : square.rank() + 1;
                boolean chain = false;
                for (int file : new int[]{square.file() - 1, square.file() + 1}) {
                    if (!inside(file, behind)) continue;
                    Piece supporter = board.get(new Square(file, behind));
                    if (supporter != null && supporter.color() == piece.color() && supporter.type() == Type.PAWN) {
                        chain = true;
                    }
                }
                if (chain && (square.file() == 3 || square.file() == 4)) {
                    patterns.add(new Pattern(Pattern.Severity.INFO,
                            "Пешечная цепь держит центр",
                            "Пешка " + square.algebraic() + " поддержана сзади. Давите на основание чужой цепи, а не на вершину.", square));
                }
            }
        }
    }

    private List<Square> occupiedOnRay(Board board, Square from, int[] direction) {
        List<Square> result = new ArrayList<>();
        int file = from.file() + direction[0], rank = from.rank() + direction[1];
        while (inside(file, rank) && result.size() < 3) {
            Square square = new Square(file, rank);
            if (board.get(square) != null) result.add(square);
            file += direction[0];
            rank += direction[1];
        }
        return result;
    }

    private boolean sameLinePiece(Type type, int[] direction) {
        boolean diagonal = direction[0] != 0 && direction[1] != 0;
        return type == Type.QUEEN || (diagonal && type == Type.BISHOP) || (!diagonal && type == Type.ROOK);
    }

    private void addUnique(List<Pattern> patterns, Set<String> found, String key, Pattern pattern) {
        if (found.add(key)) patterns.add(pattern);
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
