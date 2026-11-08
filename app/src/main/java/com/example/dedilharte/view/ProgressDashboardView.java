package com.example.dedilharte.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.example.dedilharte.data.DashboardMetrics;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native dashboard with per-group synchronization status and a local profile fallback. */
public final class ProgressDashboardView extends LinearLayout {
    public static final long ANIMATION_MS = 600;
    private static final int DARK = Color.rgb(27, 79, 76), TEAL = Color.rgb(50, 150, 145);
    private static final int CYAN = Color.rgb(84, 214, 221), SOFT = Color.rgb(224, 246, 246);
    private final TextView level;
    private final LinearLayout indicators;
    private final List<ObjectAnimator> animations = new ArrayList<>();
    private final Runnable beginner, intermediate, refresh;
    private final String localLevel;
    private final int localGoal;
    public final ImageView avatar;

    public ProgressDashboardView(Context context, String name, String localLevel, int localGoal,
            Runnable beginner, Runnable intermediate, Runnable refresh) {
        super(context);
        this.beginner = beginner; this.intermediate = intermediate; this.refresh = refresh;
        this.localLevel = localLevel == null || localLevel.trim().isEmpty() ? "Iniciante" : localLevel;
        this.localGoal = Math.max(1, Math.min(7, localGoal));
        setOrientation(VERTICAL); setPadding(dp(16), dp(12), dp(16), dp(24));
        setBackgroundColor(Color.WHITE);
        TextView heading = label("Seu progresso", 25, true);
        addView(heading, gap(0, 12));
        LinearLayout profile = card();
        profile.setOrientation(HORIZONTAL); profile.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new ImageView(context);
        avatar.setContentDescription("Foto de perfil");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(round(Color.WHITE, 32)); avatar.setClipToOutline(true);
        profile.addView(avatar, new LayoutParams(dp(60), dp(60)));
        LinearLayout identity = column();
        LayoutParams identityParams = new LayoutParams(0, -2, 1); identityParams.setMargins(dp(14), 0, 0, 0);
        profile.addView(identity, identityParams);
        identity.addView(label(name, 20, true));
        level = label("Nível atual: " + this.localLevel, 13, true);
        level.setTextColor(TEAL); level.setPadding(dp(9), dp(4), dp(9), dp(4));
        level.setBackground(round(Color.rgb(194, 236, 235), 12));
        identity.addView(level, gap(5, 0));
        addView(profile, gap(0, 12));
        indicators = column(); addView(indicators, new LayoutParams(-1, -2));
    }

    public void render(DashboardMetrics data, boolean loading, String notice) {
        cancelAnimations(); indicators.removeAllViews();
        boolean activityReady = data != null && data.hasActivity();
        boolean triviaReady = data != null && data.hasTrivia();
        boolean songsReady = data != null && data.hasSongs();
        level.setText("Nível atual: " + (data == null || !data.complete() ? localLevel : data.level()));
        String weeklyTitle = "Meta semanal: " + (activityReady ? data.accessDays : 0) + " de " + (activityReady ? data.weeklyGoal : localGoal) + " dias";
        String weeklyMessage = !activityReady ? "Acessos ainda não sincronizados" : data.accessDays >= data.weeklyGoal
                ? "Você cumpriu sua meta semanal!" : "Cada dia de prática faz diferença.";
        indicators.addView(metricCard("✓", weeklyTitle, weeklyMessage,
                activityReady ? DashboardMetrics.percentage(data.accessDays, data.weeklyGoal) : 0), gap(0, 12));
        indicators.addView(section("≋  Trivias"), gap(0, 8));
        indicators.addView(trivia("≋", "Trivia Iniciante", triviaReady ? data.beginner : null, beginner), gap(0, 10));
        indicators.addView(trivia("▥", "Trivia Intermediário", triviaReady ? data.intermediate : null, intermediate), gap(0, 12));
        indicators.addView(section("♫  Músicas"), gap(0, 8));
        LinearLayout music = card();
        music.setOrientation(HORIZONTAL); music.setGravity(Gravity.CENTER_VERTICAL);
        music.addView(icon("♫"), new LayoutParams(dp(38), dp(42)));
        LinearLayout totals = column();
        LayoutParams totalsParams = new LayoutParams(0, -2, 1); totalsParams.setMargins(dp(10), 0, dp(12), 0);
        music.addView(totals, totalsParams);
        totals.addView(label("Músicas aprendidas", 12, false));
        totals.addView(label(songsReady ? data.songs.completed + " de " + data.songs.total : "0", 22, true));
        totals.addView(label(!songsReady ? "Sincronização pendente" : data.songs.total == 0 ? "Sem músicas publicadas" : "no total", 11, false));
        LinearLayout difficulties = column();
        music.addView(difficulties, new LayoutParams(0, -2, 1.3f));
        difficulty(difficulties, "Fáceis", songsReady ? data.easy : null);
        difficulty(difficulties, "Médias", songsReady ? data.medium : null);
        difficulty(difficulties, "Difíceis", songsReady ? data.hard : null);
        indicators.addView(music, gap(0, 14));
        indicators.addView(action("TRIVIA INICIANTE", CYAN, beginner), gap(0, 9));
        indicators.addView(action("TRIVIA INTERMEDIÁRIO", DARK, intermediate), gap(0, 10));
        String status = loading ? "Atualizando indicadores…" : notice;
        if (data != null && (loading || !notice.isEmpty())) {
            status += "\nDados salvos em " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(data.savedAt));
        }
        if (!status.isEmpty()) {
            TextView message = label(status, 12, false);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            indicators.addView(message, gap(0, 8));
        }
        TextView retry = action(loading ? "ATUALIZANDO…" : "ATUALIZAR", SOFT, refresh);
        retry.setTextColor(DARK); retry.setEnabled(!loading);
        indicators.addView(retry);
    }

    private LinearLayout trivia(String glyph, String title, DashboardMetrics.Count count, Runnable open) {
        String subtitle = count == null ? "0 aulas concluídas • sincronização pendente"
                : count.completed + " de " + count.total + " aulas concluídas";
        LinearLayout view = metricCard(glyph, title, subtitle, count == null ? 0 : count.percent());
        view.setOnClickListener(v -> open.run()); view.setFocusable(true);
        view.setContentDescription(title + ". " + subtitle);
        return view;
    }

    private LinearLayout metricCard(String glyph, String title, String subtitle, Integer percent) {
        LinearLayout card = card(); card.setOrientation(HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(icon(glyph), new LayoutParams(dp(40), dp(42)));
        LinearLayout body = column();
        LayoutParams params = new LayoutParams(0, -2, 1); params.setMargins(dp(12), 0, 0, 0);
        card.addView(body, params);
        body.addView(label(title, 16, true));
        body.addView(label(subtitle, 12, false), gap(2, 7));
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(bar(percent), new LayoutParams(0, dp(8), 1));
        TextView badge = label(percent == null ? "—" : percent + "%", 12, true);
        badge.setTextColor(DARK); badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(Color.rgb(194, 236, 235), 12));
        LayoutParams badgeParams = new LayoutParams(dp(45), dp(23)); badgeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(badge, badgeParams); body.addView(row);
        return card;
    }

    private void difficulty(LinearLayout parent, String name, DashboardMetrics.Count count) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(name, 11, false), new LayoutParams(dp(45), -2));
        row.addView(bar(count == null ? 0 : count.percent()), new LayoutParams(0, dp(6), 1));
        TextView value = label(count == null ? "0" : count.completed + "/" + count.total, 11, true);
        value.setGravity(Gravity.END); row.addView(value, new LayoutParams(dp(38), -2));
        row.setContentDescription(name + ": " + (count == null ? "aguardando dados" : count.completed + " de " + count.total + " aprendidas"));
        parent.addView(row, gap(3, 3));
    }

    private ProgressBar bar(Integer percent) {
        ProgressBar bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        ClipDrawable fill = new ClipDrawable(round(CYAN, 8), Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{round(Color.rgb(185, 229, 229), 8), fill});
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers); bar.setMax(100);
        bar.setContentDescription(percent == null ? "Progresso indisponível" : percent + "% concluído");
        if (percent != null) {
            ObjectAnimator animation = ObjectAnimator.ofInt(bar, "progress", 0, percent);
            animation.setDuration(ANIMATION_MS); animation.setInterpolator(new DecelerateInterpolator());
            animations.add(animation); animation.start();
        }
        return bar;
    }
    private TextView action(String title, int color, Runnable action) {
        TextView view = label(title, 14, true); view.setTextColor(color == CYAN ? DARK : Color.WHITE);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(46));
        view.setBackground(round(color, 15)); view.setOnClickListener(v -> action.run()); view.setFocusable(true);
        return view;
    }
    private TextView icon(String value) {
        TextView view = label(value, 27, true); view.setGravity(Gravity.CENTER); view.setTextColor(TEAL);
        view.setBackground(round(Color.rgb(194, 236, 235), 12)); view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private TextView section(String value) { return label(value, 18, true); }
    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(DARK);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(VERTICAL); return view; }
    private LinearLayout card() {
        LinearLayout view = column(); view.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(233, 249, 249), SOFT});
        background.setCornerRadius(dp(18)); view.setBackground(background); return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private LayoutParams gap(int top, int bottom) { LayoutParams params = new LayoutParams(-1, -2); params.setMargins(0, dp(top), 0, dp(bottom)); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void cancelAnimations() { for (ObjectAnimator animation : animations) animation.cancel(); animations.clear(); }
    @Override protected void onDetachedFromWindow() { cancelAnimations(); super.onDetachedFromWindow(); }
}
