package com.example.dedilharte;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dedilharte.auth.SessionManager;
import com.example.dedilharte.data.ThemeStore;
import com.example.dedilharte.network.DedilharteApiClient;
import com.example.dedilharte.network.DedilharteApiService;
import com.example.dedilharte.network.model.AuthResponse;
import com.example.dedilharte.network.model.LoginRequest;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class LoginActivity extends AppCompatActivity {

    private static final String TAG = "DedilharteAuth";

    private SessionManager sessionManager;
    private DedilharteApiService api;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView createAccount;
    private CheckBox showPassword;
    private Call<AuthResponse> pendingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new ThemeStore(this).applySavedMode();
        super.onCreate(savedInstanceState);
        DedilharteApiClient.configure(getApplicationContext());
        sessionManager = new SessionManager(this);
        api = DedilharteApiClient.service();
        setContentView(R.layout.activity_login);
        com.example.dedilharte.view.WindowInsetsHelper.activity(this);
        findViewById(R.id.loginForgotPassword).setOnClickListener(v -> com.example.dedilharte.view.PasswordDialogs.forgot(this, api, emailInput.getText().toString()));

        emailInput = findViewById(R.id.loginEmail);
        passwordInput = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.loginProgress);
        createAccount = findViewById(R.id.loginCreateAccount);
        showPassword = findViewById(R.id.loginShowPassword);

        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int selection = passwordInput.getSelectionStart();
            passwordInput.setInputType(isChecked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInput.setSelection(Math.max(0, selection));
        });
        loginButton.setOnClickListener(v -> login());
        createAccount.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        if (sessionManager.isLoggedIn()) {
            validateSavedSession();
        } else {
            setLoading(false);
        }
    }

    private void validateSavedSession() {
        setLoading(true);
        pendingCall = api.me();
        pendingCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().user != null
                        && sessionManager.getUserId().equals(response.body().user.id)) {
                    sessionManager.saveSession(sessionManager.getToken(), response.body().user);
                    openMain();
                    return;
                }
                if (response.code() == 401 || response.code() == 403) {
                    sessionManager.clearSession();
                } else if (response.code() >= 500 && openOfflineIfAvailable()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(LoginActivity.this, com.example.dedilharte.network.ApiErrors.message(response,
                        "Não foi possível validar sua sessão. Tente novamente."), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                Log.e(TAG, "validacao de sessao falhou: " + t.getMessage());
                if (t instanceof IOException && openOfflineIfAvailable()) return;
                setLoading(false);
                showConnectionError(t);
            }
        });
    }

    private void login() {
        String email = emailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = passwordInput.getText().toString();

        if (!validate(email, password)) {
            return;
        }

        setLoading(true);
        pendingCall = api.login(new LoginRequest(email, password));
        pendingCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().user != null
                        && response.body().user.id != null && !response.body().user.id.trim().isEmpty()
                        && response.body().token != null && !response.body().token.trim().isEmpty()) {
                    sessionManager.saveSession(response.body().token, response.body().user);
                    openMain();
                    return;
                }
                showError(response);
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                setLoading(false);
                Log.e(TAG, "login falhou: " + t.getMessage());
                showConnectionError(t);
            }
        });
    }

    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Digite um e-mail valido");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Digite sua senha");
            return false;
        }
        return true;
    }

    private boolean openOfflineIfAvailable() {
        if (!sessionManager.canUseOfflineSession()) return false;
        Toast.makeText(this, "Servidor indisponível. Usando conteúdo salvo neste aparelho.", Toast.LENGTH_LONG).show();
        openMain();
        return true;
    }

    private void showConnectionError(Throwable error) {
        String message = error instanceof javax.net.ssl.SSLException
                ? "Não foi possível validar a conexão segura. Verifique a rede e a data do aparelho."
                : "Sem conexão com o servidor. Tente novamente.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (pendingCall != null) pendingCall.cancel();
        super.onDestroy();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        findViewById(R.id.loginForgotPassword).setEnabled(!loading);
        createAccount.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        showPassword.setEnabled(!loading);
    }

    private void showError(Response<AuthResponse> response) {
        String message;
        if (response.code() == 401) {
            message = "E-mail ou senha inválidos.";
        } else if (response.code() == 400) {
            message = "Dados inválidos.";
        } else {
            message = readErrorMessage(response, "Não foi possível concluir a operação.");
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String readErrorMessage(Response<?> response, String fallback) {
        if (response.errorBody() == null) {
            return fallback;
        }
        try {
            String body = response.errorBody().string();
            JSONObject json = new JSONObject(body);
            String error = json.optString("error", "");
            return error.trim().isEmpty() ? fallback : error;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
