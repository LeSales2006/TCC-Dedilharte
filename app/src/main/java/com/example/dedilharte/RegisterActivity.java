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
import com.example.dedilharte.network.model.RegisterRequest;

import org.json.JSONObject;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "DedilharteAuth";

    private SessionManager sessionManager;
    private DedilharteApiService api;
    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private Button registerButton;
    private ProgressBar progressBar;
    private TextView loginLink;
    private CheckBox showPassword;
    private com.example.dedilharte.view.PreferencesFields preferences;
    private boolean personalizing;
    private Call<AuthResponse> pendingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new ThemeStore(this).applySavedMode();
        super.onCreate(savedInstanceState);
        DedilharteApiClient.configure(getApplicationContext());
        sessionManager = new SessionManager(this);
        api = DedilharteApiClient.service();
        setContentView(R.layout.activity_register);
        com.example.dedilharte.view.WindowInsetsHelper.activity(this);
        preferences = new com.example.dedilharte.view.PreferencesFields(this, "beginner", 3, 10, false, true);
        ((android.widget.LinearLayout)findViewById(R.id.registerPreferences)).addView(preferences);

        nameInput = findViewById(R.id.registerName);
        emailInput = findViewById(R.id.registerEmail);
        passwordInput = findViewById(R.id.registerPassword);
        confirmPasswordInput = findViewById(R.id.registerConfirmPassword);
        registerButton = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.registerProgress);
        loginLink = findViewById(R.id.registerLoginLink);
        showPassword = findViewById(R.id.registerShowPassword);

        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int inputType = isChecked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            int passwordSelection = passwordInput.getSelectionStart();
            int confirmSelection = confirmPasswordInput.getSelectionStart();
            passwordInput.setInputType(inputType);
            confirmPasswordInput.setInputType(inputType);
            passwordInput.setSelection(Math.max(0, passwordSelection));
            confirmPasswordInput.setSelection(Math.max(0, confirmSelection));
        });
        registerButton.setOnClickListener(v -> register());
        registerButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.theme_cyan)));
        registerButton.setTextColor(getColor(R.color.theme_ink));
        loginLink.setOnClickListener(v -> { if (personalizing) showStep(false); else finish(); });
        showStep(savedInstanceState != null && savedInstanceState.getBoolean("personalizing"));
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (personalizing && (pendingCall == null || registerButton.isEnabled())) showStep(false);
                else finish();
            }
        });
        setLoading(false);
    }

    private void register() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (!validate(name, email, password, confirmPassword)) {
            if (personalizing) showStep(false);
            return;
        }

        if (!personalizing) { showStep(true); return; }
        int songs = preferences.songs(); if (songs < 1) return;
        RegisterRequest request = new RegisterRequest(name, email, password);
        request.initialLevel = preferences.level(); request.weeklyGoal = preferences.weeklyGoal.getSelectedItemPosition() + 1;
        request.songGoal = songs; request.notificationsEnabled = preferences.notifications.isChecked();
        setLoading(true);
        pendingCall = api.register(request);
        pendingCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled()) return;
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
                if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                setLoading(false);
                Log.e(TAG, "cadastro falhou: " + t.getMessage());
                Toast.makeText(RegisterActivity.this, "Sem conexão com o servidor. Tente novamente.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validate(String name, String email, String password, String confirmPassword) {
        if (name.length() < 2 || name.length() > 180) {
            nameInput.setError("Use de 2 a 180 caracteres");
            return false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Digite um e-mail valido");
            return false;
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            passwordInput.setError("Use 8 caracteres ou mais, uma letra e um número; máximo de 72 bytes");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("As senhas devem ser iguais");
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
        loginLink.setEnabled(!loading);
        nameInput.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmPasswordInput.setEnabled(!loading);
        showPassword.setEnabled(!loading);
        preferences.setInputsEnabled(!loading);
    }

    private void showError(Response<AuthResponse> response) {
        String message;
        if (response.code() == 409) {
            message = "Este e-mail já está cadastrado.";
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

    private void showStep(boolean personalize) {
        personalizing = personalize;
        for (View view : new View[]{nameInput, emailInput, passwordInput, confirmPasswordInput, showPassword}) view.setVisibility(personalize ? View.GONE : View.VISIBLE);
        findViewById(R.id.registerPreferences).setVisibility(personalize ? View.VISIBLE : View.GONE);
        registerButton.setText(personalize ? "CRIAR CONTA" : "CONTINUAR");
        loginLink.setText(personalize ? "Voltar aos dados" : "Entrar");
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("personalizing", personalizing); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { if (pendingCall != null) pendingCall.cancel(); super.onDestroy(); }
    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("request_notifications", sessionManager.notificationsEnabled());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
