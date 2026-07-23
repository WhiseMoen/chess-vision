package ru.chessvision.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.Set;

import ru.chessvision.core.*;

public final class BoardView extends View {
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

    private final int light = Color.rgb(226, 220, 194);
    private final int dark = Color.rgb(91, 116, 94);
    private final int vision = Color.argb(125, 183, 227, 107);
    private final int danger = Color.argb(155, 239, 100, 86);

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
                paint.setColor((file + rank) % 2 == 1 ? light : dark);
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
                    if (insight != null && insight.attackers() > insight.defenders()) {
                        paint.setColor(danger);
                        canvas.drawCircle(left + cell / 2, top + cell / 2, cell * .43f, paint);
                    }
                    drawPiece(canvas, piece, left, top, cell);
                }
                drawCoordinate(canvas, square, left, top, cell);
            }
        }
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
        paint.setColor((square.file() + square.rank()) % 2 == 1 ? dark : light);
        if (square.file() == 0) canvas.drawText(String.valueOf(square.rank() + 1), left + 3, top + cell * .17f, paint);
        if (square.rank() == 0) canvas.drawText(String.valueOf((char)('a' + square.file())), left + cell * .78f, top + cell * .94f, paint);
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
