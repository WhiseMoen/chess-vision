package ru.chessvision;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.util.*;

import ru.chessvision.core.*;
import ru.chessvision.ui.BoardView;

public final class MainActivity extends Activity {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";
    private static final String[] EXAMPLES = {
            START,
            "r2q1rk1/ppp2ppp/2npbn2/8/2B1P3/2NP1N2/PPP2PPP/R1BQR1K1 w - - 0 10",
            "4r1k1/ppp2ppp/2n5/3q4/3P4/2P1B3/PP3PPP/R2Q2K1 w - - 0 18",
            "8/5pk1/6p1/3N3p/4P3/5P2/6PP/6K1 w - - 0 35"
    };

    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private Board board;
    private Analysis analysis;
    private BoardView boardView;
    private LinearLayout insights;
    private LinearLayout moveHistory;
    private LinearLayout overlayChips;
    private TextView positionSummary;
    private int exampleIndex;
    private final List<String> moves = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
        board = Fen.parse(START);
        analysis = analyzer.analyze(board);
        setContentView(buildScreen());
        int savedTheme = getPreferences(MODE_PRIVATE).getInt("board_theme", 0);
        BoardView.BoardTheme[] themes = BoardView.BoardTheme.values();
        boardView.setTheme(themes[Math.max(0, Math.min(savedTheme, themes.length - 1))]);
        render();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 20, 17));
        LinearLayout root = column();
        root.setPadding(dp(16), dp(18), dp(16), dp(40));
        scroll.addView(root);

        TextView eyebrow = text("CHESS VISION · ЛАБОРАТОРИЯ ПОЗИЦИИ", 11, Color.rgb(183, 227, 107));
        eyebrow.setLetterSpacing(.12f);
        root.addView(eyebrow);

        TextView title = text("Смотрите не на ходы.\nСмотрите на возможности.", 27, Color.rgb(243, 240, 231));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(5));
        root.addView(title);

        positionSummary = text("", 14, Color.rgb(181, 190, 180));
        positionSummary.setLineSpacing(0, 1.15f);
        root.addView(positionSummary);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        Button learn = button("Обучение");
        Button play = button("Играть");
        Button library = button("Учебники");
        navigation.addView(learn, weighted());
        LinearLayout.LayoutParams playParams = weighted();
        playParams.setMargins(dp(8), 0, 0, 0);
        navigation.addView(play, playParams);
        LinearLayout.LayoutParams libraryParams = weighted();
        libraryParams.setMargins(dp(8), 0, 0, 0);
        navigation.addView(library, libraryParams);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(46));
        navParams.setMargins(0, dp(14), 0, 0);
        root.addView(navigation, navParams);
        learn.setOnClickListener(v -> startActivity(new Intent(this, TrainingActivity.class)));
        play.setOnClickListener(v -> startActivity(new Intent(this, GameActivity.class)));
        library.setOnClickListener(v -> showLibraryDialog());

        boardView = new BoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, 0);
        boardParams.height = getResources().getDisplayMetrics().widthPixels - dp(32);
        boardParams.setMargins(0, dp(16), 0, dp(12));
        root.addView(boardView, boardParams);
        boardView.setListener(new BoardView.Listener() {
            @Override public void onSquareSelected(Square square) { renderSelected(square); }
            @Override public void onMoveRequested(Square from, Square to) {
                Piece moving = board.get(from);
                Piece captured = board.get(to);
                moves.add(moveNotation(moving, from, to, captured));
                board.move(from, to);
                analysis = analyzer.analyze(board);
                render();
            }
        });

        root.addView(buildOverlaySelector());
        root.addView(buildHistoryCard());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button example = button("Другая позиция");
        Button fen = button("Вставить FEN");
        Button theme = button("Оформление");
        actions.addView(example, weighted());
        LinearLayout.LayoutParams second = weighted();
        second.setMargins(dp(8), 0, 0, 0);
        actions.addView(fen, second);
        LinearLayout.LayoutParams third = weighted();
        third.setMargins(dp(8), 0, 0, 0);
        actions.addView(theme, third);
        root.addView(actions);
        example.setOnClickListener(v -> {
            exampleIndex = (exampleIndex + 1) % EXAMPLES.length;
            board = Fen.parse(EXAMPLES[exampleIndex]);
            moves.clear();
            analysis = analyzer.analyze(board);
            render();
        });
        fen.setOnClickListener(v -> showFenDialog());
        theme.setOnClickListener(v -> showThemeDialog());

        TextView hint = text("Нажмите на фигуру: зелёным появится её поле зрения, точками — доступные поля. Красный ореол означает, что атак больше, чем защит.", 13, Color.rgb(151, 164, 151));
        hint.setPadding(0, dp(12), 0, dp(18));
        hint.setLineSpacing(0, 1.2f);
        root.addView(hint);

        TextView findings = text("Что происходит в позиции", 20, Color.rgb(243, 240, 231));
        findings.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(findings);
        insights = column();
        root.addView(insights);
        return scroll;
    }

    private void render() {
        boardView.setPosition(board, analysis);
        String side = board.sideToMove() == Piece.Color.WHITE ? "белых" : "чёрных";
        positionSummary.setText("Ход " + side + "  ·  влияние: белые " + analysis.whiteInfluence() +
                " / чёрные " + analysis.blackInfluence() + "\nКоснитесь фигуры, чтобы увидеть направления и потенциал.");
        showPatterns();
        renderHistory();
        renderOverlayChips();
    }

    private void showPatterns() {
        insights.removeAllViews();
        int count = Math.min(7, analysis.patterns().size());
        for (int i = 0; i < count; i++) addPatternCard(analysis.patterns().get(i));
    }

    private void renderSelected(Square square) {
        PieceInsight selected = analysis.pieces().get(square);
        if (selected == null) {
            showPatterns();
            return;
        }
        insights.removeAllViews();
        LinearLayout card = card();
        TextView label = text(selected.piece().type().russianName.toUpperCase() + " · " + square.algebraic(), 11, Color.rgb(183, 227, 107));
        label.setLetterSpacing(.1f);
        card.addView(label);
        TextView score = text("Потенциал " + selected.potential() + " / 100", 22, Color.WHITE);
        score.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        score.setPadding(0, dp(5), 0, dp(4));
        card.addView(score);
        card.addView(progress(selected.potential()));
        TextView details = text(selected.summary() + "\nПоле зрения: " + selected.vision().size() +
                " · атак на фигуру: " + selected.attackers() + " · защит: " + selected.defenders(), 14, Color.rgb(202, 207, 198));
        details.setPadding(0, dp(10), 0, 0);
        details.setLineSpacing(0, 1.2f);
        card.addView(details);
        insights.addView(card);

        for (Pattern pattern : analysis.patterns()) {
            if (square.equals(pattern.square())) addPatternCard(pattern);
        }
    }

    private void addPatternCard(Pattern pattern) {
        LinearLayout card = card();
        int color = switch (pattern.severity()) {
            case DANGER -> Color.rgb(239, 100, 86);
            case WARNING -> Color.rgb(245, 196, 75);
            case OPPORTUNITY -> Color.rgb(183, 227, 107);
            case INFO -> Color.rgb(118, 175, 211);
        };
        String label = switch (pattern.severity()) {
            case DANGER -> "ОПАСНОСТЬ";
            case WARNING -> "НАПРЯЖЕНИЕ";
            case OPPORTUNITY -> "ВОЗМОЖНОСТЬ";
            case INFO -> "НАБЛЮДЕНИЕ";
        };
        TextView type = text(label, 10, color);
        type.setLetterSpacing(.12f);
        card.addView(type);
        TextView title = text(pattern.title(), 17, Color.rgb(243, 240, 231));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(4), 0, dp(4));
        card.addView(title);
        TextView explanation = text(pattern.explanation(), 14, Color.rgb(181, 190, 180));
        explanation.setLineSpacing(0, 1.18f);
        card.addView(explanation);
        insights.addView(card);
        if (pattern.square() != null) card.setOnClickListener(v -> renderSelected(pattern.square()));
    }

    private void showFenDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Color.rgb(25, 32, 27));
        input.setText(START);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("Позиция в формате FEN")
                .setMessage("Можно скопировать FEN с любого шахматного сайта.")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Анализировать", (dialog, which) -> {
                    try {
                        board = Fen.parse(input.getText().toString());
                        moves.clear();
                        analysis = analyzer.analyze(board);
                        render();
                    } catch (RuntimeException error) {
                        Toast.makeText(this, "Не удалось прочитать FEN: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private View buildOverlaySelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(10));
        scroll.setLayoutParams(params);
        overlayChips = new LinearLayout(this);
        overlayChips.setOrientation(LinearLayout.HORIZONTAL);
        overlayChips.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(overlayChips);
        renderOverlayChips();
        return scroll;
    }

    private void renderOverlayChips() {
        if (overlayChips == null || boardView == null) return;
        overlayChips.removeAllViews();
        for (BoardView.OverlayMode mode : BoardView.OverlayMode.values()) {
            boolean active = boardView.overlayMode() == mode;
            TextView chip = text(mode.title, 13,
                    active ? Color.rgb(20, 25, 21) : Color.rgb(206, 213, 204));
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            chip.setPadding(dp(15), 0, dp(15), 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(active ? Color.rgb(183, 227, 107) : Color.rgb(25, 32, 27));
            bg.setStroke(dp(1), active ? Color.rgb(183, 227, 107) : Color.rgb(55, 67, 57));
            bg.setCornerRadius(dp(18));
            chip.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(36));
            p.setMargins(0, 0, dp(7), 0);
            overlayChips.addView(chip, p);
            chip.setOnClickListener(v -> {
                boardView.setOverlayMode(mode);
                renderOverlayChips();
                showOverlayExplanation(mode);
            });
        }
    }

    private View buildHistoryCard() {
        LinearLayout card = card();
        LinearLayout.LayoutParams outer = (LinearLayout.LayoutParams) card.getLayoutParams();
        outer.setMargins(0, 0, 0, dp(12));
        TextView title = text("ИСТОРИЯ ПОЗИЦИИ", 10, Color.rgb(151, 164, 151));
        title.setLetterSpacing(.12f);
        card.addView(title);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        moveHistory = new LinearLayout(this);
        moveHistory.setOrientation(LinearLayout.HORIZONTAL);
        moveHistory.setPadding(0, dp(9), 0, 0);
        scroll.addView(moveHistory);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, dp(50)));
        renderHistory();
        return card;
    }

    private void renderHistory() {
        if (moveHistory == null) return;
        moveHistory.removeAllViews();
        if (moves.isEmpty()) {
            TextView empty = text("Сделайте ход на доске — здесь появится история", 13, Color.rgb(127, 139, 127));
            empty.setGravity(Gravity.CENTER_VERTICAL);
            moveHistory.addView(empty, new LinearLayout.LayoutParams(-2, dp(40)));
            return;
        }
        for (int i = 0; i < moves.size(); i += 2) {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(35, 44, 37));
            bg.setCornerRadius(dp(8));
            pair.setBackground(bg);
            TextView number = text((i / 2 + 1) + ".", 12, Color.rgb(127, 139, 127));
            number.setPadding(dp(9), 0, dp(6), 0);
            pair.addView(number, new LinearLayout.LayoutParams(-2, dp(38)));
            pair.addView(historyMove(moves.get(i)));
            if (i + 1 < moves.size()) pair.addView(historyMove(moves.get(i + 1)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
            p.setMargins(0, 0, dp(7), 0);
            moveHistory.addView(pair, p);
        }
        moveHistory.post(() -> {
            View parent = (View) moveHistory.getParent();
            if (parent instanceof HorizontalScrollView scroll) scroll.fullScroll(View.FOCUS_RIGHT);
        });
    }

    private TextView historyMove(String notation) {
        TextView move = text(notation, 14, Color.rgb(238, 238, 231));
        move.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        move.setGravity(Gravity.CENTER);
        move.setPadding(dp(7), 0, dp(7), 0);
        return move;
    }

    private String moveNotation(Piece piece, Square from, Square to, Piece captured) {
        String symbol = switch (piece.type()) {
            case KING -> "Кр";
            case QUEEN -> "Ф";
            case ROOK -> "Л";
            case BISHOP -> "С";
            case KNIGHT -> "К";
            case PAWN -> "";
        };
        return symbol + from.algebraic() + (captured == null ? "–" : "×") + to.algebraic();
    }

    private void showThemeDialog() {
        BoardView.BoardTheme[] themes = BoardView.BoardTheme.values();
        String[] names = Arrays.stream(themes).map(t -> t.title).toArray(String[]::new);
        new AlertDialog.Builder(this)
                .setTitle("Оформление доски")
                .setSingleChoiceItems(names, boardView.theme().ordinal(), (dialog, which) -> {
                    boardView.setTheme(themes[which]);
                    getPreferences(MODE_PRIVATE).edit().putInt("board_theme", which).apply();
                    dialog.dismiss();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showLibraryDialog() {
        String[] books = {
                "Практика Chess Vision · 18 интерактивных уроков",
                "Chess Fundamentals · Х. Р. Капабланка (public domain)",
                "Chess Strategy · Эдвард Ласкер (public domain)",
                "The Blue Book of Chess · Говард Стаунтон (public domain)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Учебная библиотека")
                .setItems(books, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, TrainingActivity.class));
                    } else {
                        String[] urls = {
                                "",
                                "https://www.gutenberg.org/ebooks/33870",
                                "https://www.gutenberg.org/ebooks/5614",
                                "https://www.gutenberg.org/ebooks/16377"
                        };
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(urls[which])));
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showOverlayExplanation(BoardView.OverlayMode mode) {
        String explanation = switch (mode) {
            case OVERVIEW -> "Общий обзор угроз. Выберите фигуру, чтобы увидеть её поле зрения.";
            case FORKS -> "Жёлтые стрелки показывают фигуры, одновременно атакующие несколько ценных целей.";
            case SYNERGY -> "Синие пунктирные линии показывают взаимную поддержку и защищённые фигуры.";
            case PRESSURE -> "Красные стрелки ведут к фигурам, на которые атакующих больше, чем защитников.";
            case POWER -> "Зелёное кольцо — активная фигура, красное — фигура с низким потенциалом.";
        };
        Toast.makeText(this, explanation, Toast.LENGTH_LONG).show();
    }

    private LinearLayout card() {
        LinearLayout result = column();
        result.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(25, 32, 27));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(47, 58, 49));
        result.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        result.setLayoutParams(params);
        return result;
    }

    private View progress(int potential) {
        LinearLayout track = new LinearLayout(this);
        track.setBackgroundColor(Color.rgb(47, 58, 49));
        LinearLayout fill = new LinearLayout(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(potential >= 60 ? Color.rgb(183, 227, 107) : potential >= 35 ? Color.rgb(245, 196, 75) : Color.rgb(239, 100, 86));
        shape.setCornerRadius(dp(3));
        fill.setBackground(shape);
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(5), potential));
        track.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(5), 100 - potential));
        return track;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(20, 25, 21));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(183, 227, 107));
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        button.setMinHeight(dp(46));
        return button;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(46), 1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
