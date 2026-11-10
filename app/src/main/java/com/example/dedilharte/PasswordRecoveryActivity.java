package com.example.dedilharte;

import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.network.ApiErrors;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.model.RecoveryRequest;
import com.example.dedilharte.network.model.RecoveryResponse;
import com.example.dedilharte.view.WindowInsetsHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class PasswordRecoveryActivity extends AppCompatActivity {
    private Flow flow;
    private EditText email, code, password, confirm;
    private Button submit, resend;
    private String displayedRequest;
    private int displayedStep = -1;
    private ClipboardManager clipboard;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::pasteCode;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateButtons(); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        new ThemeStore(this).applySavedMode();
        super.onCreate(savedInstanceState);
        DedilharteApiClient.configure(getApplicationContext());
        setContentView(R.layout.activity_password_recovery);
        WindowInsetsHelper.activity(this);
        flow = new ViewModelProvider(this).get(Flow.class);
        email = findViewById(R.id.recoveryEmail); code = findViewById(R.id.recoveryCode);
        password = findViewById(R.id.recoveryPassword); confirm = findViewById(R.id.recoveryConfirm);
        submit = findViewById(R.id.recoverySubmit); resend = findViewById(R.id.recoveryResend);
        if (flow.email.isEmpty()) flow.email = getIntent().getStringExtra("email") == null ? "" : getIntent().getStringExtra("email");
        email.setText(flow.email);
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        code.setOnFocusChangeListener((view, focused) -> { if (focused) pasteCode(); });
        submit.setOnClickListener(view -> submit());
        resend.setOnClickListener(view -> flow.requestCode(flow.email));
        findViewById(R.id.recoveryBack).setOnClickListener(view -> finish());
        render();
    }

    @Override protected void onStart() {
        super.onStart(); flow.changed = this::render; render(); handler.post(ticker);
    }
    @Override protected void onResume() { super.onResume(); clipboard.addPrimaryClipChangedListener(clipListener); }
    @Override protected void onPause() { clipboard.removePrimaryClipChangedListener(clipListener); super.onPause(); }
    @Override protected void onStop() { flow.changed = null; handler.removeCallbacks(ticker); super.onStop(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && flow != null) pasteCode(); }

    private void pasteCode() {
        if (flow.step != 1 || flow.busy || !hasWindowFocus() || code.length() != 0 || !clipboard.hasPrimaryClip()) return;
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence value = clip.getItemAt(0).getText();
        if (value != null && value.toString().trim().matches("[0-9]{6}")) {
            code.setText(value.toString().trim()); code.setSelection(6);
        }
    }

    private void submit() {
        if (flow.busy || SystemClock.elapsedRealtime() < flow.retryUntil) return;
        if (flow.step == 0) {
            String address = email.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("Digite um e-mail válido"); return; }
            flow.requestCode(address);
        } else if (flow.step == 1) {
            String digits = code.getText().toString();
            if (!digits.matches("[0-9]{6}")) { code.setError("Digite os 6 dígitos"); return; }
            RecoveryRequest request = new RecoveryRequest(); request.requestId = flow.requestId; request.code = digits;
            flow.execute(DedilharteApiClient.service().verifyRecoveryCode(request), 1);
        } else if (flow.step == 2) {
            String fresh = password.getText().toString(), repeated = confirm.getText().toString();
            if (fresh.length() < 8 || fresh.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                    || !fresh.matches("(?s).*[A-Za-z].*") || !fresh.matches("(?s).*[0-9].*")) {
                password.setError("Use ao menos 8 caracteres, uma letra e um número; máximo de 72 bytes"); return;
            }
            if (!fresh.equals(repeated)) { confirm.setError("As senhas devem ser iguais"); return; }
            RecoveryRequest request = new RecoveryRequest(); request.requestId = flow.requestId;
            request.authorization = flow.authorization; request.newPassword = fresh; request.confirmPassword = repeated;
            flow.execute(DedilharteApiClient.service().resetPassword(request), 2);
        }
    }

    private void render() {
        if (displayedStep != flow.step) {
            displayedStep = flow.step;
            View focused = getCurrentFocus();
            if (focused != null) focused.clearFocus();
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), findViewById(R.id.recoveryRoot))
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime());
        }
        if (flow.step == 3) {
            new SessionManager(this).clearSession();
            Toast.makeText(this, flow.status, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class).putExtra("recoveryEmail", flow.email).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish(); return;
        }
        findViewById(R.id.recoveryEmailPanel).setVisibility(flow.step == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.recoveryCodePanel).setVisibility(flow.step == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.recoveryPasswordPanel).setVisibility(flow.step == 2 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.recoveryStep)).setText(new String[]{"1 de 3 · Informe seu e-mail", "2 de 3 · Verifique o código", "3 de 3 · Crie sua nova senha"}[flow.step]);
        ((TextView) findViewById(R.id.recoveryStatus)).setText(flow.status);
        findViewById(R.id.recoveryProgress).setVisibility(flow.busy ? View.VISIBLE : View.GONE);
        email.setEnabled(!flow.busy); code.setEnabled(!flow.busy); password.setEnabled(!flow.busy); confirm.setEnabled(!flow.busy);
        if (flow.requestId != null && !flow.requestId.equals(displayedRequest)) {
            displayedRequest = flow.requestId; code.setText(""); password.setText(""); confirm.setText("");
        }
        if (flow.step == 2) code.setText("");
        if (flow.step == 1 && !flow.busy) code.post(this::pasteCode);
        updateButtons();
    }

    private void updateButtons() {
        long now = SystemClock.elapsedRealtime();
        submit.setText(new String[]{"Enviar código", "Verificar código", "Salvar nova senha", "Concluído"}[flow.step]);
        submit.setEnabled(!flow.busy && now >= flow.retryUntil);
        resend.setVisibility(flow.step == 1 || flow.step == 2 ? View.VISIBLE : View.GONE);
        long seconds = Math.max(0, (Math.max(flow.resendAt, flow.retryUntil) - now + 999) / 1000);
        resend.setText(seconds == 0 ? "Reenviar código" : "Reenviar em " + seconds + "s");
        resend.setEnabled(!flow.busy && seconds == 0);
    }

    /** Retains only memory across rotation. Credentials/grants are never saved to disk or Bundles. */
    public static final class Flow extends ViewModel {
        int step;
        String email = "", requestId, authorization;
        String status = "Enviaremos um código de 6 dígitos para este e-mail. Ele vale por 10 minutos.";
        boolean busy;
        long resendAt, retryUntil;
        Runnable changed;
        private Call<RecoveryResponse> pending;
        void requestCode(String address) {
            if (busy || SystemClock.elapsedRealtime() < Math.max(resendAt, retryUntil)) return;
            email = address;
            RecoveryRequest request = new RecoveryRequest(); request.email = address;
            execute(DedilharteApiClient.service().requestRecoveryCode(request), 0);
        }
        void execute(Call<RecoveryResponse> call, int action) {
            busy = true; pending = call;
            status = new String[]{"Enviando código…", "Verificando código…", "Salvando nova senha…"}[action]; notifyChanged();
            call.enqueue(new Callback<RecoveryResponse>() {
                @Override public void onResponse(Call<RecoveryResponse> source, Response<RecoveryResponse> response) {
                    pending = null; busy = false;
                    RecoveryResponse body = response.body();
                    if (!response.isSuccessful()) {
                        status = response.code() == 401 || response.code() == 403 || response.code() == 404
                                ? "Recuperação indisponível no servidor. Tente novamente mais tarde."
                                : ApiErrors.message(response, "Não foi possível concluir. Tente novamente.");
                        if (response.code() == 429) {
                            int seconds = 60;
                            try { seconds = Integer.parseInt(response.headers().get("Retry-After")); } catch (Exception ignored) { }
                            retryUntil = SystemClock.elapsedRealtime() + Math.max(1, Math.min(seconds, 3600)) * 1000L;
                        }
                    } else if (body == null || (action == 0 && (body.requestId == null || body.requestId.isEmpty()))
                            || (action == 1 && (body.authorization == null || !body.authorization.matches("[a-f0-9]{64}")))
                            || (action == 2 && (body.message == null || body.message.trim().isEmpty()))) {
                        status = "Resposta incompleta do servidor. Solicite outro código.";
                    } else {
                        step = action + 1;
                        if (action == 0) {
                            requestId = body.requestId; authorization = null;
                            resendAt = SystemClock.elapsedRealtime() + Math.max(60, body.resendAfter) * 1000L;
                            status = "Código enviado para " + email + ". Confira também o spam. Válido por 10 minutos; até 5 tentativas.";
                        } else if (action == 1) {
                            authorization = body.authorization;
                            status = "Código verificado. Você tem 5 minutos para salvar uma senha com pelo menos 8 caracteres, uma letra e um número.";
                        } else {
                            authorization = null;
                            status = "Se houver uma conta elegível, a senha foi atualizada. Entre novamente.";
                        }
                    }
                    notifyChanged();
                }
                @Override public void onFailure(Call<RecoveryResponse> source, Throwable error) {
                    pending = null; busy = false;
                    if (!source.isCanceled()) { status = "Sem conexão. Confira sua rede e tente novamente."; notifyChanged(); }
                }
            });
        }
        private void notifyChanged() { if (changed != null) changed.run(); }
        @Override protected void onCleared() { if (pending != null) pending.cancel(); authorization = null; changed = null; }
    }
}
