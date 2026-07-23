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
    private TextView positionSummary;
    private int exampleIndex;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
        board = Fen.parse(START);
        analysis = analyzer.analyze(board);
        setContentView(buildScreen());
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

        boardView = new BoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, 0);
        boardParams.height = getResources().getDisplayMetrics().widthPixels - dp(32);
        boardParams.setMargins(0, dp(16), 0, dp(12));
        root.addView(boardView, boardParams);
        boardView.setListener(new BoardView.Listener() {
            @Override public void onSquareSelected(Square square) { renderSelected(square); }
            @Override public void onMoveRequested(Square from, Square to) {
                board.move(from, to);
                analysis = analyzer.analyze(board);
                render();
            }
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button example = button("Другая позиция");
        Button fen = button("Вставить FEN");
        actions.addView(example, weighted());
        LinearLayout.LayoutParams second = weighted();
        second.setMargins(dp(8), 0, 0, 0);
        actions.addView(fen, second);
        root.addView(actions);
        example.setOnClickListener(v -> {
            exampleIndex = (exampleIndex + 1) % EXAMPLES.length;
            board = Fen.parse(EXAMPLES[exampleIndex]);
            analysis = analyzer.analyze(board);
            render();
        });
        fen.setOnClickListener(v -> showFenDialog());

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
                        analysis = analyzer.analyze(board);
                        render();
                    } catch (RuntimeException error) {
                        Toast.makeText(this, "Не удалось прочитать FEN: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
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
