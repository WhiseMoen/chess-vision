package ru.chessvision.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.Set;
import java.util.List;

import ru.chessvision.core.*;

public final class BoardView extends View {
    public enum BoardTheme {
        CLASSIC("Классика", 0xFFE2DCC2, 0xFF5B745E, 0xFF1B211C),
        WOOD("Дерево", 0xFFF0D9B5, 0xFFB58863, 0xFF302117),
        BLUE("Океан", 0xFFDEE3E6, 0xFF708AA2, 0xFF16222C),
        NIGHT("Ночь", 0xFFB8C0C8, 0xFF465461, 0xFF111820),
        PURPLE("Аметист", 0xFFE4D8E8, 0xFF80658A, 0xFF261C2B);

        public final String title;
        public final int light;
        public final int dark;
        public final int frame;
        BoardTheme(String title, int light, int dark, int frame) {
            this.title = title; this.light = light; this.dark = dark; this.frame = frame;
        }
    }

    public enum OverlayMode {
        OVERVIEW("Обзор"),
        FORKS("Вилки"),
        SYNERGY("Связи"),
        PRESSURE("Давление"),
        POWER("Сила");

        public final String title;
        OverlayMode(String title) { this.title = title; }
    }

    public interface Listener {
        void onSquareSelected(Square square);
        void onMoveRequested(Square from, Square to);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PatternAnalyzer analyzer = new PatternAnalyzer();
    private Board board;
    private Analysis analysis;
    private Square selected;
    private Listener listener;

    private final int vision = Color.argb(125, 183, 227, 107);
    private final int danger = Color.argb(155, 239, 100, 86);
    private BoardTheme theme = BoardTheme.CLASSIC;
    private OverlayMode overlayMode = OverlayMode.OVERVIEW;

    public BoardView(Context context) {
        super(context);
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setPosition(Board board, Analysis analysis) {
        this.board = board;
        this.analysis = analysis;
        this.selected = null;
        invalidate();
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public Square selected() { return selected; }
    public BoardTheme theme() { return theme; }
    public OverlayMode overlayMode() { return overlayMode; }
    public void setTheme(BoardTheme theme) { this.theme = theme; invalidate(); }
    public void setOverlayMode(OverlayMode mode) {
        this.overlayMode = mode;
        this.selected = null;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, width);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;
        float cell = getWidth() / 8f;
        for (int screenRank = 0; screenRank < 8; screenRank++) {
            int rank = 7 - screenRank;
            for (int file = 0; file < 8; file++) {
                Square square = new Square(file, rank);
                float left = file * cell, top = screenRank * cell;
                paint.setColor((file + rank) % 2 == 1 ? theme.light : theme.dark);
                canvas.drawRect(left, top, left + cell, top + cell, paint);

                if (selected != null && analysis != null) {
                    PieceInsight insight = analysis.pieces().get(selected);
                    if (selected.equals(square)) {
                        paint.setColor(Color.argb(170, 245, 196, 75));
                        canvas.drawRect(left, top, left + cell, top + cell, paint);
                    } else if (insight != null && insight.vision().contains(square)) {
                        paint.setColor(vision);
                        canvas.drawRect(left, top, left + cell, top + cell, paint);
                        if (insight.moves().contains(square)) {
                            paint.setColor(Color.argb(170, 16, 20, 17));
                            canvas.drawCircle(left + cell / 2, top + cell / 2, cell * .09f, paint);
                        }
                    }
                }

                Piece piece = board.get(square);
                if (piece != null) {
                    PieceInsight insight = analysis == null ? null : analysis.pieces().get(square);
                    if (overlayMode == OverlayMode.OVERVIEW && insight != null && insight.attackers() > insight.defenders()) {
                        paint.setColor(danger);
                        canvas.drawCircle(left + cell / 2, top + cell / 2, cell * .43f, paint);
                    }
                    drawPiece(canvas, piece, left, top, cell);
                }
                drawCoordinate(canvas, square, left, top, cell);
            }
        }
        drawCues(canvas, cell);
    }

    private void drawPiece(Canvas canvas, Piece piece, float left, float top, float cell) {
        String symbol = switch (piece.type()) {
            case KING -> piece.color() == Piece.Color.WHITE ? "♔" : "♚";
            case QUEEN -> piece.color() == Piece.Color.WHITE ? "♕" : "♛";
            case ROOK -> piece.color() == Piece.Color.WHITE ? "♖" : "♜";
            case BISHOP -> piece.color() == Piece.Color.WHITE ? "♗" : "♝";
            case KNIGHT -> piece.color() == Piece.Color.WHITE ? "♘" : "♞";
            case PAWN -> piece.color() == Piece.Color.WHITE ? "♙" : "♟";
        };
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        paint.setTextSize(cell * .78f);
        paint.setColor(piece.color() == Piece.Color.WHITE ? Color.WHITE : Color.rgb(22, 27, 23));
        paint.setShadowLayer(2.5f, 0, 1.5f,
                piece.color() == Piece.Color.WHITE ? Color.rgb(40, 45, 40) : Color.argb(170, 255, 255, 255));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = top + cell / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(symbol, left + cell / 2f, baseline, paint);
        paint.clearShadowLayer();
    }

    private void drawCoordinate(Canvas canvas, Square square, float left, float top, float cell) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(cell * .16f);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor((square.file() + square.rank()) % 2 == 1 ? theme.dark : theme.light);
        if (square.file() == 0) canvas.drawText(String.valueOf(square.rank() + 1), left + 3, top + cell * .17f, paint);
        if (square.rank() == 0) canvas.drawText(String.valueOf((char)('a' + square.file())), left + cell * .78f, top + cell * .94f, paint);
    }

    private void drawCues(Canvas canvas, float cell) {
        if (analysis == null || overlayMode == OverlayMode.OVERVIEW) return;
        List<VisualCue> cues = analysis.visualCues();
        int drawn = 0;
        for (VisualCue cue : cues) {
            if (!matches(cue.type()) || drawn >= 18) continue;
            int color = cueColor(cue.type());
            if (cue.from().equals(cue.to())) {
                float cx = (cue.from().file() + .5f) * cell;
                float cy = (7 - cue.from().rank() + .5f) * cell;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cell * .075f);
                paint.setColor(color);
                canvas.drawCircle(cx, cy, cell * .40f, paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                drawArrow(canvas, cue.from(), cue.to(), cell, color, cue.type() == VisualCue.Type.SYNERGY);
            }
            drawn++;
        }
    }

    private boolean matches(VisualCue.Type type) {
        return switch (overlayMode) {
            case FORKS -> type == VisualCue.Type.FORK;
            case SYNERGY -> type == VisualCue.Type.SYNERGY;
            case PRESSURE -> type == VisualCue.Type.PRESSURE;
            case POWER -> type == VisualCue.Type.STRENGTH || type == VisualCue.Type.WEAKNESS;
            case OVERVIEW -> false;
        };
    }

    private int cueColor(VisualCue.Type type) {
        return switch (type) {
            case FORK -> Color.argb(220, 245, 196, 75);
            case SYNERGY -> Color.argb(175, 92, 180, 235);
            case PRESSURE -> Color.argb(215, 239, 100, 86);
            case STRENGTH -> Color.argb(220, 183, 227, 107);
            case WEAKNESS -> Color.argb(220, 239, 100, 86);
        };
    }

    private void drawArrow(Canvas canvas, Square from, Square to, float cell, int color, boolean dashed) {
        float sx = (from.file() + .5f) * cell;
        float sy = (7 - from.rank() + .5f) * cell;
        float ex = (to.file() + .5f) * cell;
        float ey = (7 - to.rank() + .5f) * cell;
        float dx = ex - sx, dy = ey - sy;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1) return;
        float ux = dx / length, uy = dy / length;
        sx += ux * cell * .27f; sy += uy * cell * .27f;
        ex -= ux * cell * .30f; ey -= uy * cell * .30f;

        paint.setColor(color);
        paint.setStrokeWidth(cell * .07f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(dashed ? new android.graphics.DashPathEffect(
                new float[]{cell * .13f, cell * .10f}, 0) : null);
        canvas.drawLine(sx, sy, ex, ey, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);

        float head = cell * .20f;
        Path path = new Path();
        path.moveTo(ex, ey);
        path.lineTo(ex - ux * head - uy * head * .55f, ey - uy * head + ux * head * .55f);
        path.lineTo(ex - ux * head + uy * head * .55f, ey - uy * head - ux * head * .55f);
        path.close();
        canvas.drawPath(path, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || board == null) return true;
        int file = Math.min(7, Math.max(0, (int) (event.getX() / (getWidth() / 8f))));
        int rank = 7 - Math.min(7, Math.max(0, (int) (event.getY() / (getWidth() / 8f))));
        Square touched = new Square(file, rank);
        if (selected != null) {
            Set<Square> moves = analyzer.pseudoMoves(board, selected);
            if (moves.contains(touched)) {
                Square from = selected;
                selected = null;
                if (listener != null) listener.onMoveRequested(from, touched);
                invalidate();
                return true;
            }
        }
        selected = board.get(touched) == null ? null : touched;
        if (listener != null) listener.onSquareSelected(touched);
        invalidate();
        return true;
    }
}
