package ru.chessvision;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.*;
import java.util.stream.Collectors;

import ru.chessvision.core.*;
import ru.chessvision.learn.*;
import ru.chessvision.ui.BoardView;

public final class TrainingActivity extends Activity {
    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private final List<Lesson> all = LessonCatalog.all();
    private List<Lesson> visible = List.of();
    private int lessonIndex;
    private Spinner courseSpinner;
    private BoardView boardView;
    private TextView counter;
    private TextView title;
    private TextView idea;
    private TextView question;
    private TextView feedback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
        setContentView(buildScreen());
        chooseCourse(0);
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
        TextView heading = text("Тренировка паттернов", 27, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        TextView sub = text("Смотрите на отношения фигур сейчас, затем проверяйте ход.", 14, Color.rgb(171, 182, 171));
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        courseSpinner = new Spinner(this);
        List<String> courses = all.stream().map(Lesson::course).distinct().collect(Collectors.toList());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, courses);
        courseSpinner.setAdapter(adapter);
        courseSpinner.setBackgroundColor(Color.rgb(226, 220, 194));
        root.addView(courseSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        courseSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                chooseCourse(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        counter = text("", 11, Color.rgb(183, 227, 107));
        counter.setLetterSpacing(.1f);
        counter.setPadding(0, dp(14), 0, dp(3));
        root.addView(counter);
        title = text("", 21, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        idea = text("", 14, Color.rgb(198, 205, 196));
        idea.setLineSpacing(0, 1.18f);
        idea.setPadding(0, dp(6), 0, dp(12));
        root.addView(idea);

        boardView = new BoardView(this);
        int boardSize = getResources().getDisplayMetrics().widthPixels - dp(32);
        root.addView(boardView, new LinearLayout.LayoutParams(-1, boardSize));
        boardView.setListener(new BoardView.Listener() {
            @Override public void onSquareSelected(Square square) {}
            @Override public void onMoveRequested(Square from, Square to) { checkMove(from, to); }
        });

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Вилки", "Связи", "Давление", "Сила"};
        BoardView.OverlayMode[] values = {BoardView.OverlayMode.FORKS, BoardView.OverlayMode.SYNERGY,
                BoardView.OverlayMode.PRESSURE, BoardView.OverlayMode.POWER};
        for (int i = 0; i < labels.length; i++) {
            Button button = smallButton(labels[i]);
            BoardView.OverlayMode mode = values[i];
            button.setOnClickListener(v -> boardView.setOverlayMode(
                    boardView.overlayMode() == mode ? BoardView.OverlayMode.OVERVIEW : mode));
            modes.addView(button, weighted(i > 0));
        }
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, dp(42));
        modeParams.setMargins(0, dp(8), 0, 0);
        root.addView(modes, modeParams);

        LinearLayout prompt = card();
        TextView promptLabel = text("ВОПРОС К ПОЗИЦИИ", 10, Color.rgb(245, 196, 75));
        promptLabel.setLetterSpacing(.1f);
        prompt.addView(promptLabel);
        question = text("", 15, Color.WHITE);
        question.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        question.setPadding(0, dp(5), 0, dp(5));
        prompt.addView(question);
        feedback = text("Выберите фигуру или включите подходящий визуальный слой.", 13, Color.rgb(155, 166, 155));
        feedback.setLineSpacing(0, 1.15f);
        prompt.addView(feedback);
        root.addView(prompt);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = button("Назад");
        Button hint = button("Подсказка");
        Button solution = button("Ответ");
        Button next = button("Дальше");
        actions.addView(prev, weighted(false));
        actions.addView(hint, weighted(true));
        actions.addView(solution, weighted(true));
        actions.addView(next, weighted(true));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(46));
        actionParams.setMargins(0, dp(10), 0, 0);
        root.addView(actions, actionParams);
        prev.setOnClickListener(v -> showLesson(Math.max(0, lessonIndex - 1)));
        next.setOnClickListener(v -> showLesson(Math.min(visible.size() - 1, lessonIndex + 1)));
        hint.setOnClickListener(v -> feedback.setText("Подсказка: " + current().hint()));
        solution.setOnClickListener(v -> feedback.setText("Разбор: " + current().answer()));
        return scroll;
    }

    private void chooseCourse(int position) {
        if (courseSpinner == null) return;
        String course = (String) courseSpinner.getItemAtPosition(position);
        visible = all.stream().filter(l -> l.course().equals(course)).collect(Collectors.toList());
        showLesson(0);
    }

    private void showLesson(int index) {
        if (visible.isEmpty() || boardView == null) return;
        lessonIndex = index;
        Lesson lesson = current();
        Board board = Fen.parse(lesson.fen());
        boardView.setOverlayMode(BoardView.OverlayMode.OVERVIEW);
        boardView.setPosition(board, analyzer.analyze(board));
        long solved = visible.stream().filter(this::isSolved).count();
        counter.setText(lesson.course().toUpperCase() + " · " + (index + 1) + "/" + visible.size() +
                " · решено " + solved);
        title.setText(lesson.title());
        idea.setText(lesson.idea());
        question.setText(lesson.question());
        feedback.setText(lesson.interactive()
                ? "Сделайте ответный ход на доске."
                : "Исследуйте фигуры и включайте слои. Затем откройте подсказку или разбор.");
    }

    private void checkMove(Square from, Square to) {
        Lesson lesson = current();
        if (!lesson.interactive()) return;
        boolean correct = from.algebraic().equals(lesson.expectedFrom()) && to.algebraic().equals(lesson.expectedTo());
        if (correct) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(progressKey(lesson), true).apply();
            feedback.setText("Верно! " + lesson.answer() + "\nСледующая позиция откроется автоматически.");
            counter.setText(lesson.course().toUpperCase() + " · решено " +
                    visible.stream().filter(this::isSolved).count() + "/" + visible.size());
            if (lessonIndex + 1 < visible.size()) handler.postDelayed(() -> showLesson(lessonIndex + 1), 1100);
        } else {
            feedback.setText("Этот ход не реализует главную идею. " + lesson.hint());
        }
    }

    private Lesson current() { return visible.get(lessonIndex); }
    private boolean isSolved(Lesson lesson) {
        return getPreferences(MODE_PRIVATE).getBoolean(progressKey(lesson), false);
    }
    private String progressKey(Lesson lesson) { return lesson.course() + "|" + lesson.title(); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout result = column();
        result.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(25, 32, 27));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(47, 58, 49));
        result.setBackground(background);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, 0);
        result.setLayoutParams(p);
        return result;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setTextColor(Color.rgb(20, 25, 21));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(183, 227, 107));
        bg.setCornerRadius(dp(9));
        b.setBackground(bg);
        return b;
    }

    private Button smallButton(String value) {
        Button b = button(value);
        b.setTextSize(10);
        return b;
    }

    private LinearLayout.LayoutParams weighted(boolean margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        if (margin) p.setMargins(dp(6), 0, 0, 0);
        return p;
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
