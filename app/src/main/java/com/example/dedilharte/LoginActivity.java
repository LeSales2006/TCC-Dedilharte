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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DedilharteApiClient.configure(getApplicationContext());
        sessionManager = new SessionManager(this);
        api = DedilharteApiClient.service();
        setContentView(R.layout.activity_login);

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
        api.me().enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    sessionManager.saveSession(sessionManager.getToken(), response.body().user);
                    openMain();
                    return;
                }
                sessionManager.clearSession();
                setLoading(false);
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "validacao de sessao falhou: " + t.getMessage());
                setLoading(false);
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
        api.login(new LoginRequest(email, password)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    sessionManager.saveSession(response.body().token, response.body().user);
                    openMain();
                    return;
                }
                showError(response);
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Log.e(TAG, "login falhou: " + t.getMessage());
                Toast.makeText(LoginActivity.this, "Sem conexão com o servidor. Tente novamente.", Toast.LENGTH_LONG).show();
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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
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
