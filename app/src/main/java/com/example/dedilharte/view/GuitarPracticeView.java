package com.example.dedilharte.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.example.dedilharte.data.TablatureParser;
import com.example.dedilharte.model.TablatureEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagrama animado que mostra a nota atual e a proxima nota no braco do violao.
 */
public class GuitarPracticeView extends View {

    public interface OnStepListener {
        void onStep(int position, int stringNumber);
    }

    public interface OnTabEventListener {
        void onEvent(int position, TablatureEvent event);

        void onFinished();
    }

    private static final int DARK = Color.rgb(27, 79, 76);
    private static final int CYAN = Color.rgb(84, 214, 221);
    private static final int PRESSED_STRING = Color.rgb(126, 69, 214);
    private static final int NEXT_PREVIEW = Color.argb(64, 80, 80, 80);
    private static final int INACTIVE_STRING = Color.BLACK;
    private static final int FRET = Color.rgb(38, 38, 38);
    private static final int MAX_FRET = 12;
    private static final String[] STRING_LABELS = {"E", "B", "G", "D", "A", "E"};
    private static final float[] STRING_WIDTHS = {2.35f, 2.18f, 2.0f, 1.82f, 1.64f, 1.46f};
    private float currentStringGap;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RectF neckBounds = new RectF();
    private List<TablatureEvent> events = new ArrayList<>();
    private int[] pattern = new int[]{6, 3, 2, 1};
    private int currentEventIndex;
    private int bpm = 60;
    private int columnsPerBeat = TablatureParser.DEFAULT_COLUMNS_PER_BEAT;
    private boolean running;
    private boolean looping;
    private long startedAtMs;
    private long pausedAtMs;
    private OnStepListener listener;
    private OnTabEventListener tabEventListener;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running || events.isEmpty()) {
                return;
            }

            long elapsedMs = SystemClock.uptimeMillis() - startedAtMs;
            if (looping) {
                long loopDuration = loopDurationMs();
                if (loopDuration > 0L && elapsedMs >= loopDuration) {
                    long completedLoops = elapsedMs / loopDuration;
                    startedAtMs += completedLoops * loopDuration;
                    elapsedMs -= completedLoops * loopDuration;
                    currentEventIndex = 0;
                    notifyStep();
                    invalidate();
                }
            }
            int resolvedIndex = eventIndexForElapsed(elapsedMs);
            if (resolvedIndex != currentEventIndex) {
                currentEventIndex = resolvedIndex;
                notifyStep();
                invalidate();
            }

            if (!looping && isPastEnd(elapsedMs)) {
                running = false;
                pausedAtMs = 0L;
                handler.removeCallbacks(this);
                invalidate();
                if (tabEventListener != null) {
                    tabEventListener.onFinished();
                }
                return;
            }

            handler.postDelayed(this, 16L);
        }
    };

    public GuitarPracticeView(Context context) {
        super(context);
        initialize();
    }

    public GuitarPracticeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setMinimumHeight(dp(560));
        setContentDescription("Diagrama interativo das casas e cordas do violao");
        setFocusable(true);
        setBackgroundColor(Color.WHITE);
        setPattern(pattern);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int desiredHeight = Math.max(dp(560), Math.min(dp(680), Math.round(width * 1.55f)));
        int resolvedWidth = resolveSize(width, widthMeasureSpec);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    public void setPattern(int[] newPattern) {
        pause();
        pattern = newPattern == null || newPattern.length == 0
                ? new int[]{6, 3, 2, 1}
                : Arrays.copyOf(newPattern, newPattern.length);
        setTablatureText(tablatureFromPattern(pattern));
    }

    public void setTablatureText(String tablatureText) {
        pause();
        events = TablatureParser.parse(tablatureText);
        currentEventIndex = 0;
        pausedAtMs = 0L;
        invalidate();
        notifyStep();
    }

    public void setColumnsPerBeat(int columnsPerBeat) {
        this.columnsPerBeat = Math.max(1, columnsPerBeat);
        if (running) {
            startedAtMs = SystemClock.uptimeMillis() - pausedAtMs;
        }
        invalidate();
    }

    public void moveToPreviousEvent() {
        moveToEvent(currentEventIndex - 1);
    }

    public void moveToNextEvent() {
        moveToEvent(currentEventIndex + 1);
    }

    public void moveToEvent(int eventIndex) {
        pause();
        if (events.isEmpty()) {
            currentEventIndex = 0;
            pausedAtMs = 0L;
            invalidate();
            return;
        }
        if (looping) {
            int size = events.size();
            currentEventIndex = ((eventIndex % size) + size) % size;
        } else {
            currentEventIndex = Math.max(0, Math.min(events.size() - 1, eventIndex));
        }
        pausedAtMs = timeForEvent(currentEventIndex);
        notifyStep();
        invalidate();
    }

    public void setBpm(int newBpm) {
        long elapsedColumns = elapsedColumns();
        bpm = Math.max(30, Math.min(240, newBpm));
        pausedAtMs = timeForColumnOffset(elapsedColumns);
        if (running) {
            startedAtMs = SystemClock.uptimeMillis() - pausedAtMs;
        }
        invalidate();
    }

    public int getBpm() {
        return bpm;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public int getCurrentEventIndex() {
        return currentEventIndex;
    }

    public int getEventCount() {
        return events.size();
    }

    public TablatureEvent getCurrentTabEvent() {
        return currentEvent();
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running || events.isEmpty()) {
            return;
        }
        running = true;
        startedAtMs = SystemClock.uptimeMillis() - pausedAtMs;
        notifyStep();
        invalidate();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    public void pause() {
        if (running) {
            pausedAtMs = SystemClock.uptimeMillis() - startedAtMs;
        }
        running = false;
        handler.removeCallbacks(ticker);
        invalidate();
    }

    public void reset() {
        pause();
        currentEventIndex = 0;
        pausedAtMs = 0L;
        notifyStep();
        invalidate();
    }

    public void setOnStepListener(OnStepListener listener) {
        this.listener = listener;
    }

    public void setOnTabEventListener(OnTabEventListener listener) {
        this.tabEventListener = listener;
    }

    private int eventIndexForElapsed(long elapsedMs) {
        int index = 0;
        for (int i = 0; i < events.size(); i++) {
            if (timeForEvent(i) <= elapsedMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private boolean isPastEnd(long elapsedMs) {
        if (events.isEmpty()) {
            return true;
        }
        return elapsedMs >= loopDurationMs();
    }

    private long loopDurationMs() {
        if (events.isEmpty()) {
            return 0L;
        }
        return timeForEvent(events.size() - 1) + Math.min(700L, Math.round(60000d / bpm));
    }

    private long timeForEvent(int eventIndex) {
        if (events.isEmpty()) {
            return 0L;
        }
        return TablatureParser.eventTimeMs(events.get(eventIndex), firstColumn(), bpm, columnsPerBeat);
    }

    private long elapsedColumns() {
        long elapsedMs = running ? SystemClock.uptimeMillis() - startedAtMs : pausedAtMs;
        double msPerColumn = (60000d / bpm) / columnsPerBeat;
        return Math.round(elapsedMs / msPerColumn);
    }

    private long timeForColumnOffset(long columns) {
        return Math.round(columns * ((60000d / bpm) / columnsPerBeat));
    }

    private int firstColumn() {
        return events.isEmpty() ? 0 : events.get(0).getColumn();
    }

    private void notifyStep() {
        if (events.isEmpty()) {
            return;
        }
        TablatureEvent event = events.get(currentEventIndex);
        if (listener != null && !event.getNotes().isEmpty()) {
            listener.onStep(currentEventIndex, stringNumberFromIndex(event.getNotes().get(0).getStringIndex()));
        }
        if (tabEventListener != null) {
            tabEventListener.onEvent(currentEventIndex, event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float centerX = width / 2f;
        currentStringGap = Math.min(dp(33), Math.max(dp(21), width * .145f));
        float neckWidth = currentStringGap * 5f;
        float top = dp(8);
        float blockHeight = Math.max(dp(22), Math.min(dp(30), currentStringGap * .82f));
        float blockBottom = top + blockHeight;
        neckBounds.set(
                centerX - neckWidth / 2f,
                blockBottom + dp(2),
                centerX + neckWidth / 2f,
                getHeight() - dp(10)
        );
        float fretGap = fretGap();

        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        drawHeadstock(canvas, centerX, top, blockHeight);
        drawFrets(canvas, fretGap);
        drawStrings(canvas, currentEvent(), nextEvent());
        drawFretNumbers(canvas, fretGap);
        drawEvent(canvas, currentEvent(), false);
        drawEvent(canvas, nextEvent(), true);
    }

    private void drawHeadstock(Canvas canvas, float centerX, float top, float blockHeight) {
        float blockWidth = Math.max(dp(92), currentStringGap * 5f + dp(32));
        RectF target = new RectF(
                centerX - blockWidth / 2f,
                top,
                centerX + blockWidth / 2f,
                top + blockHeight
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        canvas.drawRoundRect(target, dp(5), dp(5), paint);
    }

    private void drawFrets(Canvas canvas, float fretGap) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        for (int fret = 0; fret <= MAX_FRET; fret++) {
            paint.setStrokeWidth(dp(fret == 0 ? 5 : 3));
            paint.setColor(FRET);
            float y = neckBounds.top + fret * fretGap;
            canvas.drawLine(neckBounds.left - dp(16), y, neckBounds.right + dp(16), y, paint);
        }
    }

    private void drawStrings(Canvas canvas, TablatureEvent current, TablatureEvent next) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            float x = getStringX(stringIndex);
            paint.setStrokeWidth(Math.max(dp(1), dp(STRING_WIDTHS[stringIndex])));
            paint.setColor(INACTIVE_STRING);
            canvas.drawLine(x, neckBounds.top, x, neckBounds.bottom, paint);
        }
        if (current == null) {
            return;
        }
        for (TablatureEvent.Note note : current.getNotes()) {
            int visualIndex = visualStringIndex(note.getStringIndex());
            float x = getStringX(visualIndex);
            paint.setStrokeWidth(Math.max(dp(1), dp(STRING_WIDTHS[visualIndex])));
            paint.setColor(note.getFret() == 0 ? CYAN : PRESSED_STRING);
            canvas.drawLine(x, neckBounds.top, x, neckBounds.bottom, paint);
        }
    }

    private int colorForString(int stringIndex, TablatureEvent current) {
        TablatureEvent.Note note = noteForString(current, stringIndex);
        if (note != null) {
            return note.getFret() == 0 ? CYAN : PRESSED_STRING;
        }
        return INACTIVE_STRING;
    }

    private void drawFretNumbers(Canvas canvas, float fretGap) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(11));
        paint.setColor(DARK);
        paint.setTextAlign(Paint.Align.RIGHT);
        for (int fret = 1; fret <= 3; fret++) {
            canvas.drawText(String.valueOf(fret), neckBounds.left - dp(22), neckBounds.top + (fret - .5f) * fretGap + dp(4), paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawEvent(
            Canvas canvas,
            TablatureEvent event,
            boolean next
    ) {
        if (event == null) {
            return;
        }
        for (TablatureEvent.Note note : event.getNotes()) {
            if (note.getFret() == 0) {
                continue;
            }
            float x = getStringX(visualStringIndex(note.getStringIndex()));
            float y = getFretCenterY(note.getFret());
            float radius = Math.max(dp(7), Math.min(dp(12), currentStringGap * .38f));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(next ? NEXT_PREVIEW : CYAN);
            canvas.drawCircle(x, y, next ? radius * .88f : radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(next ? 1 : 2));
            paint.setColor(next ? Color.argb(82, 70, 70, 70) : DARK);
            canvas.drawCircle(x, y, next ? radius * .88f : radius, paint);
        }
    }

    private TablatureEvent.Note noteForString(TablatureEvent event, int stringIndex) {
        if (event == null) {
            return null;
        }
        for (TablatureEvent.Note note : event.getNotes()) {
            if (note.getStringIndex() == stringIndex) {
                return note;
            }
        }
        return null;
    }

    public float getStringX(int stringIndex) {
        if (stringIndex < 0) {
            stringIndex = 0;
        } else if (stringIndex > 5) {
            stringIndex = 5;
        }
        return neckBounds.left + (neckBounds.width() / 5f) * stringIndex;
    }

    private int visualStringIndex(int musicalStringIndex) {
        if (musicalStringIndex < 0) {
            musicalStringIndex = 0;
        } else if (musicalStringIndex > 5) {
            musicalStringIndex = 5;
        }
        return 5 - musicalStringIndex;
    }

    public float getFretY(int fretNumber) {
        return getFretCenterY(fretNumber);
    }

    public float getFretCenterY(int fretNumber) {
        if (fretNumber <= 0) {
            return neckBounds.top - dp(8);
        }
        int clampedFret = Math.min(fretNumber, MAX_FRET);
        return neckBounds.top + (clampedFret - .5f) * fretGap();
    }

    private float fretGap() {
        return neckBounds.height() / MAX_FRET;
    }

    private TablatureEvent currentEvent() {
        if (events.isEmpty() || currentEventIndex >= events.size()) {
            return null;
        }
        return events.get(currentEventIndex);
    }

    private TablatureEvent nextEvent() {
        int nextIndex = currentEventIndex + 1;
        if (events.isEmpty()) {
            return null;
        }
        if (nextIndex >= events.size()) {
            if (!looping) {
                return null;
            }
            nextIndex = 0;
        }
        return events.get(nextIndex);
    }

    private String tablatureFromPattern(int[] pattern) {
        int columnsPerEvent = TablatureParser.DEFAULT_COLUMNS_PER_BEAT;
        int width = Math.max(1, (pattern.length - 1) * columnsPerEvent + 1);
        StringBuilder[] lines = new StringBuilder[6];
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            lines[stringIndex] = new StringBuilder();
            for (int column = 0; column < width; column++) {
                lines[stringIndex].append('-');
            }
        }
        for (int i = 0; i < pattern.length; i++) {
            int stringIndex = pattern[i] - 1;
            if (stringIndex >= 0 && stringIndex < 6) {
                lines[stringIndex].setCharAt(i * columnsPerEvent, '0');
            }
        }
        StringBuilder result = new StringBuilder();
        for (int stringIndex = 0; stringIndex < 6; stringIndex++) {
            if (stringIndex > 0) {
                result.append('\n');
            }
            result.append(STRING_LABELS[stringIndex]).append('|').append(lines[stringIndex]).append('|');
        }
        return result.toString();
    }

    private int stringNumberFromIndex(int stringIndex) {
        return stringIndex + 1;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }
}
