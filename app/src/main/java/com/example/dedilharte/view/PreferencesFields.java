package com.example.dedilharte.view;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.widget.*;
import com.example.dedilharte.R;

/** Reused by registration and profile; declared level never changes the calculated level. */
public final class PreferencesFields extends LinearLayout {
    public final Spinner initialLevel, weeklyGoal;
    public final EditText songGoal;
    public final CheckBox notifications;
    public PreferencesFields(Context context, String level, int weekly, int songs, boolean notify, boolean includeWeekly) {
        super(context); setOrientation(VERTICAL); setBackgroundResource(R.drawable.bg_profile_field);
        int pad = Math.round(14 * getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        initialLevel = choice("Nível inicial declarado", new String[]{"Iniciante", "Intermediário"}, R.id.preferenceLevel);
        initialLevel.setSelection("intermediate".equals(level) ? 1 : 0);
        if (includeWeekly) {
            weeklyGoal = choice("Meta semanal", new String[]{"1 dia", "2 dias", "3 dias", "4 dias", "5 dias", "6 dias", "7 dias"}, R.id.preferenceWeeklyGoal);
            weeklyGoal.setSelection(Math.max(0, Math.min(6, weekly - 1)));
        } else weeklyGoal = null;
        label("Meta pessoal de músicas");
        songGoal = new EditText(context); songGoal.setId(R.id.preferenceSongGoal);
        songGoal.setInputType(InputType.TYPE_CLASS_NUMBER); songGoal.setSingleLine(true);
        songGoal.setTextColor(getContext().getColor(R.color.theme_ink)); songGoal.setText(String.valueOf(songs));
        songGoal.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.theme_ink)));
        songGoal.setContentDescription("Meta pessoal de músicas"); songGoal.setMinHeight(pad * 4); addView(songGoal);
        notifications = new CheckBox(context); notifications.setId(R.id.preferenceNotifications);
        notifications.setText("Quero receber notificações"); notifications.setTextColor(getContext().getColor(R.color.theme_ink));
        notifications.setButtonTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.theme_ink)));
        notifications.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        notifications.setChecked(notify); addView(notifications);
        label("O nível calculado acompanha seu progresso e é independente do nível declarado.");
    }
    private void label(String text) {
        TextView label = new TextView(getContext()); label.setText(text); label.setTextSize(16);
        label.setPadding(0, Math.round(8 * getResources().getDisplayMetrics().density), 0, 0);
        label.setTextColor(getContext().getColor(R.color.theme_ink)); addView(label);
    }
    private Spinner choice(String title, String[] choices, int id) {
        label(title); Spinner spinner = new Spinner(getContext()); spinner.setId(id); spinner.setContentDescription(title);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.theme_ink)));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_dropdown_item, choices) {
            @Override public android.view.View getView(int position, android.view.View convert, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convert, parent); view.setTextColor(getContext().getColor(R.color.theme_ink)); view.setBackgroundColor(getContext().getColor(R.color.theme_card_white)); return view;
            }
            @Override public android.view.View getDropDownView(int position, android.view.View convert, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convert, parent); view.setTextColor(getContext().getColor(R.color.theme_ink)); view.setBackgroundColor(getContext().getColor(R.color.theme_card_white)); return view;
            }
        };
        spinner.setAdapter(adapter); spinner.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density)); addView(spinner); return spinner;
    }
    public String level() { return initialLevel.getSelectedItemPosition() == 1 ? "intermediate" : "beginner"; }
    public int songs() {
        try { int value = Integer.parseInt(songGoal.getText().toString()); if (value > 0) return value; } catch (NumberFormatException ignored) {}
        songGoal.setError("Digite um inteiro positivo"); return -1;
    }
    public void setInputsEnabled(boolean enabled) {
        initialLevel.setEnabled(enabled); if (weeklyGoal != null) weeklyGoal.setEnabled(enabled);
        songGoal.setEnabled(enabled); notifications.setEnabled(enabled);
    }
}
