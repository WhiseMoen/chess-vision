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
import android.view.View;
import android.widget.*;

import java.util.*;

import ru.chessvision.core.*;
import ru.chessvision.game.*;
import ru.chessvision.ui.BoardView;

public final class GameActivity extends Activity {
    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private final PatternBot bot = new PatternBot();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Opening> openings = OpeningCatalog.all();
    private Board board;
    private BoardView boardView;
    private Spinner openingSpinner;
    private TextView openingIdea;
    private TextView status;
    private TextView history;
    private final List<String> moves = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
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

        boardView = new BoardView(this);
        boardView.setAllowedColor(Piece.Color.WHITE);
        int size = getResources().getDisplayMetrics().widthPixels - dp(32);
        root.addView(boardView, new LinearLayout.LayoutParams(-1, size));
        boardView.setListener(new BoardView.Listener() {
            @Override public void onSquareSelected(Square square) {}
            @Override public void onMoveRequested(Square from, Square to) { humanMove(from, to); }
        });

        LinearLayout engine = card();
        TextView engineName = text("VISION BOT · ПОЗИЦИОННЫЙ", 10, Color.rgb(183, 227, 107));
        engineName.setLetterSpacing(.1f);
        engine.addView(engineName);
        status = text("", 14, Color.WHITE);
        status.setPadding(0, dp(5), 0, dp(4));
        engine.addView(status);
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
        board = opening.position();
        moves.clear();
        openingIdea.setText(opening.family() + "\n" + opening.idea() + "\nЛиния старта: " + opening.line());
        status.setText("Ваш ход белыми. Ищите развитие, давление и улучшение худшей фигуры.");
        history.setText("Продолжение партии появится здесь.");
        render();
    }

    private void humanMove(Square from, Square to) {
        if (board.sideToMove() != Piece.Color.WHITE) return;
        Piece captured = board.get(to);
        moves.add(notation(from, to, captured));
        board.move(from, to);
        boardView.setInputEnabled(false);
        status.setText("Vision Bot рассматривает текущие возможности фигур…");
        render();
        handler.postDelayed(this::botMove, 350);
    }

    private void botMove() {
        PatternBot.Move choice = bot.choose(board);
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

    private void render() {
        boardView.setPosition(board, analyzer.analyze(board));
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i % 2 == 0) line.append(i / 2 + 1).append(". ");
            line.append(moves.get(i)).append(i % 2 == 0 ? "  " : "   ");
        }
        if (!moves.isEmpty()) history.setText(line);
    }

    private String notation(Square from, Square to, Piece captured) {
        return from.algebraic() + (captured == null ? "–" : "×") + to.algebraic();
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
