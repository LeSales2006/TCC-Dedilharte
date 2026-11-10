package com.example.dedilharte.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.dedilharte.network.ApiErrors;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.PasswordRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class PasswordDialogs {
    private PasswordDialogs() {}
    public static void forgot(Activity activity, DedilharteApiService api, String address) {
        show(activity, api, address, null);
    }
    public static void change(Activity activity, DedilharteApiService api, Runnable onChanged) {
        show(activity, api, null, onChanged);
    }
    private static EditText input(Activity activity, LinearLayout form, String hint, int type) {
        EditText field = new EditText(activity); field.setHint(hint); field.setContentDescription(hint);
        field.setInputType(type); field.setSingleLine(true); field.setTextColor(activity.getColor(com.example.dedilharte.R.color.theme_page_text));
        field.setHintTextColor(activity.getColor(com.example.dedilharte.R.color.theme_page_text)); form.addView(field); return field;
    }
    private static void show(Activity activity, DedilharteApiService api, String address, Runnable changed) {
        boolean change = changed != null;
        String token = new com.example.dedilharte.auth.SessionManager(activity).getToken();
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(activity.getColor(com.example.dedilharte.R.color.theme_page));
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density); form.setPadding(pad, pad, pad, pad);
        int password = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText first = input(activity, form, change ? "Senha atual" : "E-mail", change ? password : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!change) first.setText(address);
        EditText fresh = change ? input(activity, form, "Nova senha", password) : null;
        EditText confirm = change ? input(activity, form, "Confirmar nova senha", password) : null;
        TextView status = new TextView(activity); status.setTextColor(activity.getColor(com.example.dedilharte.R.color.theme_page_text));
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText(change ? "Use pelo menos 8 caracteres, uma letra e um número. Após salvar, entre novamente em seus dispositivos."
                : "Você receberá um link válido por 30 minutos, se houver uma conta com esse e-mail.");
        form.addView(status);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity, com.example.dedilharte.R.style.Theme_Dedilharte_PasswordDialog).setTitle(change ? "Trocar senha" : "Recuperar senha")
                .setView(scroll).setNegativeButton("Cancelar", null).setPositiveButton(change ? "Salvar senha" : "Enviar link", null).create();
        final Call<?>[] pending = new Call<?>[1];
        dialog.setOnDismissListener(d -> { if (pending[0] != null) pending[0].cancel(); });
        dialog.setOnShowListener(d -> {
            WindowInsetsHelper.dialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                PasswordRequest request = new PasswordRequest();
                if (change) {
                    request.currentPassword = first.getText().toString(); request.newPassword = fresh.getText().toString(); request.confirmPassword = confirm.getText().toString();
                    if (request.currentPassword.isEmpty()) { first.setError("Informe a senha atual"); return; }
                    if (request.newPassword.length() < 8 || request.newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                            || !request.newPassword.matches(".*[A-Za-z].*") || !request.newPassword.matches(".*[0-9].*")) {
                        fresh.setError("Use 8 caracteres ou mais, uma letra e um número; máximo de 72 bytes"); return;
                    }
                    if (!request.newPassword.equals(request.confirmPassword)) { confirm.setError("As senhas devem ser iguais"); return; }
                } else {
                    request.email = first.getText().toString().trim();
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(request.email).matches()) { first.setError("Digite um e-mail válido"); return; }
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                first.setEnabled(false); if (change) { fresh.setEnabled(false); confirm.setEnabled(false); }
                status.setText(change ? "Salvando senha…" : "Solicitando link…");
                Call<Void> call = change ? api.changePassword(request, "Bearer " + token) : api.forgotPassword(request); pending[0] = call;
                call.enqueue(new Callback<Void>() {
                    private boolean available() { return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed(); }
                    private void error(String message) {
                        if (!available()) return; status.setText(message); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        first.setEnabled(true); if (change) { fresh.setEnabled(true); confirm.setEnabled(true); }
                    }
                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!available()) return;
                        if (!response.isSuccessful()) {
                            // Recovery is public: a 401/403/404 means server configuration, not an expired user session.
                            error(!change && (response.code() == 401 || response.code() == 403 || response.code() == 404)
                                    ? "Recuperação indisponível no servidor. Tente novamente mais tarde."
                                    : ApiErrors.message(response, "Não foi possível concluir. Tente novamente."));
                            return;
                        }
                        if (change) { Toast.makeText(activity, "Senha alterada. Entre novamente.", Toast.LENGTH_LONG).show(); dialog.dismiss(); changed.run(); }
                        else { status.setText("Se houver uma conta com esse e-mail, enviaremos um link para redefinir a senha. Verifique também o spam."); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Fechar"); }
                    }
                    @Override public void onFailure(Call<Void> call, Throwable error) { if (!call.isCanceled()) error("Sem conexão. Confira sua rede e tente novamente."); }
                });
            });
        });
        dialog.show();
    }
}
