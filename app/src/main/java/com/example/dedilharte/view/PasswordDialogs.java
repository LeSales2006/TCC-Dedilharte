package com.example.dedilharte.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.dedilharte.R;
import com.example.dedilharte.network.ApiErrors;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.PasswordRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Authenticated password change; public recovery lives in PasswordRecoveryActivity. */
public final class PasswordDialogs {
    private PasswordDialogs() {}
    private static EditText input(Activity activity, LinearLayout form, String hint) {
        EditText field = new EditText(activity); field.setHint(hint); field.setContentDescription(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true); field.setTextColor(activity.getColor(R.color.theme_page_text));
        field.setHintTextColor(activity.getColor(R.color.theme_page_text)); form.addView(field); return field;
    }
    public static void change(Activity activity, DedilharteApiService api, Runnable changed) {
        String token = new com.example.dedilharte.auth.SessionManager(activity).getToken();
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(activity.getColor(R.color.theme_page));
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density); form.setPadding(pad, pad, pad, pad);
        EditText current = input(activity, form, "Senha atual");
        EditText fresh = input(activity, form, "Nova senha");
        EditText confirm = input(activity, form, "Confirmar nova senha");
        TextView status = new TextView(activity); status.setTextColor(activity.getColor(R.color.theme_page_text));
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText("Use pelo menos 8 caracteres, uma letra e um número. Após salvar, entre novamente em seus dispositivos.");
        form.addView(status);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_Dedilharte_PasswordDialog).setTitle("Trocar senha")
                .setView(scroll).setNegativeButton("Cancelar", null).setPositiveButton("Salvar senha", null).create();
        final Call<?>[] pending = new Call<?>[1];
        dialog.setOnDismissListener(d -> { if (pending[0] != null) pending[0].cancel(); });
        dialog.setOnShowListener(d -> {
            WindowInsetsHelper.dialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                PasswordRequest request = new PasswordRequest();
                request.currentPassword = current.getText().toString(); request.newPassword = fresh.getText().toString(); request.confirmPassword = confirm.getText().toString();
                if (request.currentPassword.isEmpty()) { current.setError("Informe a senha atual"); return; }
                if (request.newPassword.length() < 8 || request.newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                        || !request.newPassword.matches("(?s).*[A-Za-z].*") || !request.newPassword.matches("(?s).*[0-9].*")) {
                    fresh.setError("Use 8 caracteres ou mais, uma letra e um número; máximo de 72 bytes"); return;
                }
                if (!request.newPassword.equals(request.confirmPassword)) { confirm.setError("As senhas devem ser iguais"); return; }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                current.setEnabled(false); fresh.setEnabled(false); confirm.setEnabled(false); status.setText("Salvando senha…");
                Call<Void> call = api.changePassword(request, "Bearer " + token); pending[0] = call;
                call.enqueue(new Callback<Void>() {
                    private boolean available() { return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed(); }
                    private void error(String message) {
                        if (!available()) return; status.setText(message); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        current.setEnabled(true); fresh.setEnabled(true); confirm.setEnabled(true);
                    }
                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!available()) return;
                        if (!response.isSuccessful()) { error(ApiErrors.message(response, "Não foi possível concluir. Tente novamente.")); return; }
                        Toast.makeText(activity, "Senha alterada. Entre novamente.", Toast.LENGTH_LONG).show(); dialog.dismiss(); changed.run();
                    }
                    @Override public void onFailure(Call<Void> call, Throwable failure) { if (!call.isCanceled()) error("Sem conexão. Confira sua rede e tente novamente."); }
                });
            });
        });
        dialog.show();
    }
}
