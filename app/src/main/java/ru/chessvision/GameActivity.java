package ru.chessvision;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import java.util.*;
import java.util.concurrent.*;

import ru.chessvision.core.*;
import ru.chessvision.game.*;
import ru.chessvision.network.*;
import ru.chessvision.ui.BoardView;

public final class GameActivity extends Activity {
    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private final PatternBot bot = new PatternBot();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService botExecutor = Executors.newSingleThreadExecutor();
    private final List<Opening> openings = OpeningCatalog.all();
    private final StockfishCloudClient stockfish = new StockfishCloudClient();
    private final OpeningExplorerClient explorer = new OpeningExplorerClient();
    private Board board;
    private BoardView boardView;
    private Spinner openingSpinner;
    private TextView openingIdea;
    private TextView status;
    private TextView history;
    private TextView openingStats;
    private TextView visionEval;
    private TextView stockfishEval;
    private Switch hintSwitch;
    private Spinner hintLayer;
    private final List<String> moves = new ArrayList<>();
    private int evalGeneration;
    private int openingGeneration;
    private int gameGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
        explorer.setBearerToken(getSharedPreferences("network", MODE_PRIVATE)
                .getString("lichess_token", ""));
        setContentView(buildScreen());
        startOpening(0);
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 20, 17));
        LinearLayout root = column();
        root.setPadding(dp(16), dp(18), dp(16), dp(36));
        scroll.addView(root);

        TextView back = text("‹  CHESS VISION", 12, Color.rgb(183, 227, 107));
        back.setPadding(0, 0, 0, dp(10));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        TextView heading = text("Партия из дебюта", 27, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        TextView sub = text("Выберите структуру и играйте против Vision Bot — он оценивает активность здесь и сейчас.", 14, Color.rgb(171, 182, 171));
        sub.setPadding(0, dp(4), 0, dp(13));
        root.addView(sub);

        openingSpinner = new Spinner(this);
        ArrayAdapter<Opening> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, openings);
        openingSpinner.setAdapter(adapter);
        openingSpinner.setBackgroundColor(Color.rgb(226, 220, 194));
        root.addView(openingSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        openingSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                startOpening(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        openingIdea = text("", 13, Color.rgb(196, 204, 194));
        openingIdea.setLineSpacing(0, 1.15f);
        openingIdea.setPadding(0, dp(10), 0, dp(10));
        root.addView(openingIdea);
        openingStats = text("Статистика дебюта: загрузка…", 12, Color.rgb(151, 164, 151));
        openingStats.setPadding(0, 0, 0, dp(10));
        openingStats.setOnClickListener(v -> showLichessTokenDialog());
        root.addView(openingStats);

        boardView = new BoardView(this);
        boardView.setAllowedColor(Piece.Color.WHITE);
        int size = getResources().getDisplayMetrics().widthPixels - dp(32);
        root.addView(boardView, new LinearLayout.LayoutParams(-1, size));
        boardView.setListener(new BoardView.Listener() {
            @Override public void onSquareSelected(Square square) {}
            @Override public void onMoveRequested(Square from, Square to) { humanMove(from, to); }
        });

        hintSwitch = new Switch(this);
        hintSwitch.setText("  Режим подсказок: слабости, давление и сильные поля");
        hintSwitch.setTextColor(Color.rgb(216, 221, 212));
        hintSwitch.setTextSize(13);
        hintSwitch.setPadding(0, dp(8), 0, 0);
        root.addView(hintSwitch, new LinearLayout.LayoutParams(-1, dp(50)));
        hintSwitch.setOnCheckedChangeListener((button, enabled) -> {
            boardView.setHintsEnabled(enabled);
            hintLayer.setEnabled(enabled);
            boardView.setOverlayMode(enabled ? selectedHintLayer() : BoardView.OverlayMode.OVERVIEW);
        });
        hintLayer = new Spinner(this);
        String[] hintNames = {"Давление и слабые фигуры", "Сила и низкий потенциал",
                "Синергия и взаимная защита", "Вилки и двойные атаки"};
        hintLayer.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, hintNames));
        hintLayer.setBackgroundColor(Color.rgb(226, 220, 194));
        hintLayer.setEnabled(false);
        root.addView(hintLayer, new LinearLayout.LayoutParams(-1, dp(44)));
        hintLayer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (hintSwitch.isChecked()) boardView.setOverlayMode(selectedHintLayer());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        LinearLayout engine = card();
        TextView engineName = text("VISION BOT · ПОЗИЦИОННЫЙ", 10, Color.rgb(183, 227, 107));
        engineName.setLetterSpacing(.1f);
        engine.addView(engineName);
        status = text("", 14, Color.WHITE);
        status.setPadding(0, dp(5), 0, dp(4));
        engine.addView(status);
        visionEval = text("", 13, Color.rgb(183, 227, 107));
        visionEval.setPadding(0, dp(5), 0, 0);
        engine.addView(visionEval);
        stockfishEval = text("Stockfish Cloud: ожидание позиции", 13, Color.rgb(118, 175, 211));
        stockfishEval.setPadding(0, dp(4), 0, dp(4));
        engine.addView(stockfishEval);
        history = text("", 13, Color.rgb(167, 177, 166));
        history.setLineSpacing(0, 1.15f);
        engine.addView(history);
        root.addView(engine);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button restart = button("Начать заново");
        Button engineInfo = button("Движки");
        actions.addView(restart, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(46), 1);
        second.setMargins(dp(8), 0, 0, 0);
        actions.addView(engineInfo, second);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(46));
        ap.setMargins(0, dp(10), 0, 0);
        root.addView(actions, ap);
        restart.setOnClickListener(v -> startOpening(openingSpinner.getSelectedItemPosition()));
        engineInfo.setOnClickListener(v -> showEngineDialog());
        return scroll;
    }

    private void startOpening(int index) {
        if (boardView == null) return;
        Opening opening = openings.get(Math.max(0, index));
        gameGeneration++;
        board = opening.position();
        moves.clear();
        openingIdea.setText(opening.family() + " · " + opening.variation() + "\n" + opening.idea() +
                (opening.moves().length == 0 ? "" : "\nЛиния старта: " + opening.line()));
        openingStats.setText("Статистика мастеров: загрузка…");
        status.setText("Ваш ход белыми. Ищите развитие, давление и улучшение худшей фигуры.");
        history.setText("Продолжение партии появится здесь.");
        render();
        loadOpeningStats();
    }

    private void humanMove(Square from, Square to) {
        if (board.sideToMove() != Piece.Color.WHITE) return;
        Board beforeBoard = board.copy();
        Analysis before = analyzer.analyze(board);
        Piece captured = board.get(to);
        moves.add(notation(from, to, captured));
        board.move(from, to);
        Analysis after = analyzer.analyze(board);
        boardView.setInputEnabled(false);
        status.setText(describeChange(before, beforeBoard, after, board, Piece.Color.WHITE) +
                "\nVision Bot рассматривает ответ…");
        render();
        int generation = gameGeneration;
        Board position = board.copy();
        handler.postDelayed(() -> botExecutor.execute(() -> {
            PatternBot.Move choice = bot.choose(position);
            handler.post(() -> {
                if (generation == gameGeneration && !isFinishing()) applyBotMove(choice);
            });
        }), 250);
    }

    private void applyBotMove(PatternBot.Move choice) {
        if (choice == null) {
            status.setText("У компьютера нет доступных ходов в упрощённой модели.");
            return;
        }
        Piece captured = board.get(choice.to());
        moves.add(notation(choice.from(), choice.to(), captured));
        board.move(choice.from(), choice.to());
        status.setText("Компьютер: " + choice.reason() + ". Ваш ход.");
        boardView.setInputEnabled(true);
        render();
    }

    @Override protected void onDestroy() {
        gameGeneration++;
        botExecutor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void render() {
        Analysis current = analyzer.analyze(board);
        boardView.setPosition(board, current);
        visionEval.setText(visionSummary(current));
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i % 2 == 0) line.append(i / 2 + 1).append(". ");
            line.append(moves.get(i)).append(i % 2 == 0 ? "  " : "   ");
        }
        if (!moves.isEmpty()) history.setText(line);
        requestStockfish();
    }

    private String visionSummary(Analysis analysis) {
        int balance = analysis.whiteInfluence() - analysis.blackInfluence();
        int whiteWeak = 0, blackWeak = 0, mobilityWhite = 0, mobilityBlack = 0;
        for (PieceInsight insight : analysis.pieces().values()) {
            if (insight.piece().color() == Piece.Color.WHITE) {
                mobilityWhite += insight.moves().size();
                if (insight.attackers() > insight.defenders()) whiteWeak++;
            } else {
                mobilityBlack += insight.moves().size();
                if (insight.attackers() > insight.defenders()) blackWeak++;
            }
        }
        return "Vision сейчас: влияние " + signed(balance) + " · мобильность " +
                mobilityWhite + ":" + mobilityBlack + " · слабости " + whiteWeak + ":" + blackWeak;
    }

    private String describeChange(Analysis before, Board beforeBoard, Analysis after, Board afterBoard, Piece.Color side) {
        int mobilityBefore = mobility(before, side), mobilityAfter = mobility(after, side);
        int synergyBefore = synergies(before, beforeBoard, side);
        int synergyAfter = synergies(after, afterBoard, side);
        int influenceBefore = side == Piece.Color.WHITE ? before.whiteInfluence() : before.blackInfluence();
        int influenceAfter = side == Piece.Color.WHITE ? after.whiteInfluence() : after.blackInfluence();
        return "Эффект хода: мобильность " + signed(mobilityAfter - mobilityBefore) +
                ", связи " + signed(synergyAfter - synergyBefore) +
                ", влияние " + signed(influenceAfter - influenceBefore) + ".";
    }

    private int mobility(Analysis analysis, Piece.Color side) {
        return analysis.pieces().values().stream()
                .filter(p -> p.piece().color() == side).mapToInt(p -> p.moves().size()).sum();
    }

    private int synergies(Analysis analysis, Board position, Piece.Color side) {
        return (int) analysis.visualCues().stream()
                .filter(c -> c.type() == VisualCue.Type.SYNERGY)
                .filter(c -> position.get(c.from()) != null && position.get(c.from()).color() == side)
                .count();
    }

    private String signed(int value) { return value > 0 ? "+" + value : String.valueOf(value); }

    private void requestStockfish() {
        int generation = ++evalGeneration;
        stockfishEval.setText("Stockfish Cloud: поиск глубокой оценки…");
        stockfish.evaluate(board.copy(), new StockfishCloudClient.Callback() {
            @Override public void onResult(StockfishCloudClient.Result result) {
                if (generation != evalGeneration) return;
                String score = result.mate() ? (result.pawns() > 0 ? "мат белых" : "мат чёрных")
                        : String.format(Locale.US, "%+.2f", result.pawns());
                String pv = result.line().isBlank() ? "" : " · PV " + trimPv(result.line());
                stockfishEval.setText("Stockfish Cloud: " + score + " для стороны хода · глубина " + result.depth() + pv);
            }
            @Override public void onUnavailable(String reason) {
                if (generation == evalGeneration) stockfishEval.setText("Stockfish Cloud: " + reason);
            }
        });
    }

    private String trimPv(String pv) {
        String[] moves = pv.split(" ");
        return String.join(" ", Arrays.copyOf(moves, Math.min(6, moves.length)));
    }

    private void loadOpeningStats() {
        int generation = ++openingGeneration;
        explorer.load(board.copy(), new OpeningExplorerClient.Callback() {
            @Override public void onResult(OpeningExplorerClient.Stats stats) {
                if (generation != openingGeneration) return;
                StringBuilder text = new StringBuilder("Мастера: ")
                        .append(stats.whitePercent()).append("% белые · ")
                        .append(stats.drawPercent()).append("% ничьи · ")
                        .append(stats.blackPercent()).append("% чёрные");
                if (!stats.moves().isEmpty()) {
                    text.append("\nВарианты: ");
                    for (int i = 0; i < Math.min(4, stats.moves().size()); i++) {
                        OpeningExplorerClient.MoveStat move = stats.moves().get(i);
                        if (i > 0) text.append("  ");
                        long total = Math.max(1, move.total());
                        text.append(move.san()).append(" ")
                                .append(Math.round(move.white() * 100.0 / total)).append("/")
                                .append(Math.round(move.draws() * 100.0 / total)).append("/")
                                .append(Math.round(move.black() * 100.0 / total)).append("%");
                    }
                }
                openingStats.setText(text);
            }
            @Override public void onUnavailable(String reason) {
                if (generation == openingGeneration) openingStats.setText("Статистика дебюта: " + reason + ".");
            }
        });
    }

    private void showLichessTokenDialog() {
        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setHint("Lichess API token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(getSharedPreferences("network", MODE_PRIVATE).getString("lichess_token", ""));
        new AlertDialog.Builder(this)
                .setTitle("Статистика Lichess")
                .setMessage("Opening Explorer требует авторизацию. Токен хранится только в закрытых данных приложения и не попадает в репозиторий.")
                .setView(token)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String value = token.getText().toString().trim();
                    getSharedPreferences("network", MODE_PRIVATE).edit()
                            .putString("lichess_token", value).apply();
                    explorer.setBearerToken(value);
                    loadOpeningStats();
                })
                .setNeutralButton("Удалить", (dialog, which) -> {
                    getSharedPreferences("network", MODE_PRIVATE).edit().remove("lichess_token").apply();
                    explorer.setBearerToken("");
                    loadOpeningStats();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private String notation(Square from, Square to, Piece captured) {
        return from.algebraic() + (captured == null ? "–" : "×") + to.algebraic();
    }

    private BoardView.OverlayMode selectedHintLayer() {
        return switch (hintLayer.getSelectedItemPosition()) {
            case 1 -> BoardView.OverlayMode.POWER;
            case 2 -> BoardView.OverlayMode.SYNERGY;
            case 3 -> BoardView.OverlayMode.FORKS;
            default -> BoardView.OverlayMode.PRESSURE;
        };
    }

    private void showEngineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Шахматные движки")
                .setMessage("Сейчас встроен Vision Bot: он выбирает ход по взятиям, безопасности, мобильности и влиянию без глубокого перебора.\n\n" +
                        "Stockfish в APK пока не встроен. Он распространяется по GPLv3 и требует поставки точного исходного кода вместе с бинарником.\n\n" +
                        "Android-движки из других приложений используют OEX. Поддержка выбора установленного OEX-движка подготовлена как следующий модуль; до неё можно установить совместимый пакет из Google Play.")
                .setPositiveButton("Найти OEX", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://search?q=OEX chess engine")));
                    } catch (RuntimeException error) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/search?q=OEX%20chess%20engine&c=apps")));
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout result = column();
        result.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(25, 32, 27));
        bg.setStroke(dp(1), Color.rgb(47, 58, 49));
        bg.setCornerRadius(dp(12));
        result.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, 0);
        result.setLayoutParams(p);
        return result;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setTextColor(Color.rgb(20, 25, 21));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(183, 227, 107));
        bg.setCornerRadius(dp(9));
        b.setBackground(bg);
        return b;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
