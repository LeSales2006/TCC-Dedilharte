package com.example.dedilharte.view;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dedilharte.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Public student registration only. Acceptance is never restored or cached. */
public final class TermsDialog {
    private TermsDialog() {}

    public static AlertDialog show(AppCompatActivity activity, Runnable accepted) {
        String document;
        try (InputStream input = activity.getResources().openRawResource(R.raw.student_terms_2026_11_11);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            document = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (document.trim().isEmpty()) throw new java.io.IOException("Empty terms");
        } catch (java.io.IOException error) {
            Toast.makeText(activity, R.string.student_terms_load_error, Toast.LENGTH_LONG).show();
            return null;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.StudentTermsDialog);
        View content = android.view.LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_student_terms, null);
        ((TextView) content.findViewById(R.id.studentTermsText)).setText(document);
        ScrollView scroll = content.findViewById(R.id.studentTermsScroll);
        CheckBox agree = content.findViewById(R.id.studentTermsAccept);
        View submit = content.findViewById(R.id.studentTermsSubmit);
        AlertDialog dialog = builder.setView(content).create();
        Runnable checkBottom = () -> {
            if (scroll.getHeight() > 0 && !scroll.canScrollVertically(1)) agree.setEnabled(true);
        };
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> checkBottom.run());
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> scroll.post(checkBottom));
        agree.setOnCheckedChangeListener((button, checked) -> submit.setEnabled(agree.isEnabled() && checked));
        content.findViewById(R.id.studentTermsCancel).setOnClickListener(v -> dialog.dismiss());
        submit.setOnClickListener(v -> {
            if (!agree.isEnabled() || !agree.isChecked()) return;
            submit.setEnabled(false);
            dialog.dismiss();
            accepted.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
        WindowInsetsHelper.dialog(dialog);
        return dialog;
    }
}
