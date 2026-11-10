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
import com.example.dedilharte.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native dashboard with per-group synchronization status and a local profile fallback. */
public final class ProgressDashboardView extends LinearLayout {
    public static final long ANIMATION_MS = 600;
    private final int DARK, TEAL;
    private final int CYAN, SOFT;
    private final TextView level;
    private final LinearLayout indicators;
    private final List<ObjectAnimator> animations = new ArrayList<>();
    private final Runnable beginner, intermediate, refresh;
    private final String localLevel;
    private final int localGoal, songGoal;
    public final ImageView avatar;

    public ProgressDashboardView(Context context, String name, String localLevel, int localGoal,
            Runnable beginner, Runnable intermediate, Runnable refresh) {
        this(context, name, localLevel, localGoal, 10, "beginner", beginner, intermediate, refresh);
    }
    public ProgressDashboardView(Context context, String name, String localLevel, int localGoal, int songGoal, String initialLevel,
            Runnable beginner, Runnable intermediate, Runnable refresh) {
        super(context);
        DARK = context.getColor(R.color.theme_ink); TEAL = context.getColor(R.color.theme_teal);
        CYAN = context.getColor(R.color.theme_cyan); SOFT = context.getColor(R.color.theme_dashboard_card);
        this.songGoal = Math.max(1, songGoal);
        this.beginner = beginner; this.intermediate = intermediate; this.refresh = refresh;
        this.localLevel = localLevel == null || localLevel.trim().isEmpty() ? "Iniciante" : localLevel;
        this.localGoal = Math.max(1, Math.min(7, localGoal));
        setOrientation(VERTICAL); setPadding(dp(16), dp(12), dp(16), dp(24));
        setBackgroundColor(context.getColor(R.color.theme_page));
        TextView heading = label("Seu progresso", 25, true);
        heading.setTextColor(context.getColor(R.color.theme_page_text));
        addView(heading, gap(0, 12));
        LinearLayout profile = card();
        profile.setOrientation(HORIZONTAL); profile.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new ImageView(context);
        avatar.setContentDescription("Foto de perfil");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(round(getContext().getColor(R.color.theme_card_white), 32)); avatar.setClipToOutline(true);
        profile.addView(avatar, new LayoutParams(dp(60), dp(60)));
        LinearLayout identity = column();
        LayoutParams identityParams = new LayoutParams(0, -2, 1); identityParams.setMargins(dp(14), 0, 0, 0);
        profile.addView(identity, identityParams);
        identity.addView(label(name, 20, true));
        level = label("Nível calculado: " + this.localLevel, 13, true);
        level.setTextColor(DARK); level.setPadding(dp(9), dp(4), dp(9), dp(4));
        level.setBackground(round(getContext().getColor(R.color.theme_badge), 12));
        identity.addView(level, gap(5, 0));
        identity.addView(label("Nível inicial declarado: " + ("intermediate".equals(initialLevel) ? "Intermediário" : "Iniciante"), 12, false), gap(5, 0));
        addView(profile, gap(0, 12));
        indicators = column(); addView(indicators, new LayoutParams(-1, -2));
    }

    public void render(DashboardMetrics data, boolean loading, String notice) {
        cancelAnimations(); indicators.removeAllViews();
        boolean activityReady = data != null && data.hasActivity();
        boolean triviaReady = data != null && data.hasTrivia();
        boolean songsReady = data != null && data.hasSongs();
        level.setText("Nível calculado: " + (data == null || !data.complete() ? localLevel : data.level()));
        String weeklyTitle = "Meta semanal: " + (activityReady ? data.accessDays : 0) + " de " + (activityReady ? data.weeklyGoal : localGoal) + " dias";
        String weeklyMessage = !activityReady ? "Acessos ainda não sincronizados" : data.accessDays >= data.weeklyGoal
                ? "Você cumpriu sua meta semanal!" : "Cada dia de prática faz diferença.";
        indicators.addView(metricCard(R.drawable.ic_ms_check_circle, weeklyTitle, weeklyMessage,
                activityReady ? DashboardMetrics.percentage(data.accessDays, data.weeklyGoal) : 0), gap(0, 12));
        indicators.addView(section("Trivias", R.drawable.ic_ms_school), gap(0, 8));
        indicators.addView(trivia(R.drawable.ic_ms_school, "Trivia Iniciante", triviaReady ? data.beginner : null, beginner), gap(0, 10));
        indicators.addView(trivia(R.drawable.ic_ms_quiz, "Trivia Intermediário", triviaReady ? data.intermediate : null, intermediate), gap(0, 12));
        indicators.addView(section("Músicas", R.drawable.ic_ms_music_note), gap(0, 8));
        int learned = songsReady ? data.songs.completed : 0;
        indicators.addView(metricCard(R.drawable.ic_ms_music_note, "Músicas aprendidas", learned + " de " + songGoal + " músicas • meta pessoal" + (songsReady ? "" : " • sincronização pendente"),
                DashboardMetrics.percentage(learned, songGoal)), gap(0, 14));
        indicators.addView(action("TRIVIA INICIANTE", CYAN, beginner), gap(0, 9));
        indicators.addView(action("TRIVIA INTERMEDIÁRIO", DARK, intermediate), gap(0, 10));
        String status = loading ? "Atualizando indicadores…" : notice;
        if (data != null && (loading || !notice.isEmpty())) {
            status += "\nDados salvos em " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(data.savedAt));
        }
        if (!status.isEmpty()) {
            TextView message = label(status, 12, false);
            message.setTextColor(getContext().getColor(R.color.theme_page_text));
            MaterialIcons.leading(message, loading ? R.drawable.ic_ms_refresh : R.drawable.ic_ms_info, getContext().getColor(R.color.theme_page));
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            indicators.addView(message, gap(0, 8));
        }
        TextView retry = action(loading ? "ATUALIZANDO…" : "ATUALIZAR", SOFT, refresh);
        retry.setTextColor(DARK); retry.setEnabled(!loading);
        indicators.addView(retry);
    }

    private LinearLayout trivia(int glyph, String title, DashboardMetrics.Count count, Runnable open) {
        String subtitle = count == null ? "0 aulas concluídas • sincronização pendente"
                : count.completed + " de " + count.total + " aulas concluídas";
        LinearLayout view = metricCard(glyph, title, subtitle, count == null ? 0 : count.percent());
        view.setOnClickListener(v -> open.run()); view.setFocusable(true);
        view.setContentDescription(title + ". " + subtitle);
        return view;
    }

    private LinearLayout metricCard(int glyph, String title, String subtitle, Integer percent) {
        LinearLayout card = card(); card.setOrientation(HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(icon(glyph), new LayoutParams(dp(40), dp(40)));
        LinearLayout body = column();
        LayoutParams params = new LayoutParams(0, -2, 1); params.setMargins(dp(12), 0, 0, 0);
        card.addView(body, params);
        body.addView(label(title, 16, true));
        body.addView(label(subtitle, 12, false), gap(2, 7));
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(bar(percent), new LayoutParams(0, dp(8), 1));
        TextView badge = label(percent == null ? "—" : percent + "%", 12, true);
        badge.setTextColor(DARK); badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(getContext().getColor(R.color.theme_badge), 12));
        LayoutParams badgeParams = new LayoutParams(dp(45), dp(23)); badgeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(badge, badgeParams); body.addView(row);
        return card;
    }

    private ProgressBar bar(Integer percent) {
        ProgressBar bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        ClipDrawable fill = new ClipDrawable(round(CYAN, 8), Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{round(getContext().getColor(R.color.theme_progress_track), 8), fill});
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
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        MaterialIcons.leading(view, action == beginner ? R.drawable.ic_ms_school
                : action == intermediate ? R.drawable.ic_ms_quiz : R.drawable.ic_ms_refresh, color);
        return view;
    }
    private ImageView icon(int resource) {
        ImageView view = new ImageView(getContext());
        view.setImageDrawable(MaterialIcons.drawable(getContext(), resource, DARK));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE); view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setBackground(round(getContext().getColor(R.color.theme_badge), 12)); view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private TextView section(String value, int resource) {
        TextView view = label(value, 18, true); view.setTextColor(getContext().getColor(R.color.theme_page_text)); MaterialIcons.leading(view, resource, getContext().getColor(R.color.theme_page)); return view;
    }
    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(DARK);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(VERTICAL); return view; }
    private LinearLayout card() {
        LinearLayout view = column(); view.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{getContext().getColor(R.color.theme_dashboard_gradient), SOFT});
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
