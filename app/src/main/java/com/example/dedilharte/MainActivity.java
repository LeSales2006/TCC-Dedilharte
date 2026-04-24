package com.example.dedilharte; // Ajuste para seu pacote

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {

    private ImageView logo;
    private TextView titleText;
    private TextView welcomeText;
    private TextView infoText;
    private TextView nameLabel;
    private TextInputLayout textInputLayout;
    private TextInputEditText nameInput;
    private Button okButton;

    private Handler blinkHandler = new Handler();
    private Runnable blinkRunnable;
    private boolean isButtonBlinking = false;
    private int originalButtonColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        startSequentialAnimation();
    }

    private void initializeViews() {
        logo = findViewById(R.id.logo);
        titleText = findViewById(R.id.titleText);
        welcomeText = findViewById(R.id.welcomeText);
        infoText = findViewById(R.id.infoText);
        nameLabel = findViewById(R.id.nameLabel);
        textInputLayout = findViewById(R.id.textInputLayout);
        nameInput = findViewById(R.id.nameInput);
        okButton = findViewById(R.id.okButton);

        // Guardar a cor original do botão
        if (okButton.getBackground() != null) {
            // Se tiver drawable, vai usar animação de escala apenas
            originalButtonColor = -1; // Flag para indicar que é drawable
        }

        // Inicialmente esconder todos os elementos
        setViewsVisibility(View.INVISIBLE);
    }

    private void setViewsVisibility(int visibility) {
        logo.setVisibility(visibility);
        titleText.setVisibility(visibility);
        welcomeText.setVisibility(visibility);
        infoText.setVisibility(visibility);
        nameLabel.setVisibility(visibility);
        textInputLayout.setVisibility(visibility);
        okButton.setVisibility(visibility);
    }

    private void startSequentialAnimation() {
        // Animação do Logo com fade e translação
        animateView(logo, 0, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animateView(titleText, 200, new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animateView(welcomeText, 200, new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                animateView(infoText, 200, new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        animateView(nameLabel, 200, new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                animateView(textInputLayout, 200, new AnimatorListenerAdapter() {
                                                    @Override
                                                    public void onAnimationEnd(Animator animation) {
                                                        animateView(okButton, 200, new AnimatorListenerAdapter() {
                                                            @Override
                                                            public void onAnimationEnd(Animator animation) {
                                                                startButtonBlinking();
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void animateView(final View view, int delay, AnimatorListenerAdapter listener) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(50f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(listener)
                .start();
    }

    private void startButtonBlinking() {
        isButtonBlinking = true;

        blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isButtonBlinking && okButton.getVisibility() == View.VISIBLE) {
                    blinkButton();
                    blinkHandler.postDelayed(this, 5000); // A cada 5 segundos
                }
            }
        };

        blinkHandler.post(blinkRunnable);
    }

    private void blinkButton() {
        // Efeito de escala (não afeta transparência)
        okButton.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        okButton.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start();
                    }
                })
                .start();

        // Animação de "pulo" (translação)
        okButton.animate()
                .translationY(-10f)
                .setDuration(150)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        okButton.animate()
                                .translationY(0f)
                                .setDuration(150)
                                .start();
                    }
                })
                .start();
    }

    // MÉTODO PRINCIPAL DO CLIQUE DO BOTÃO - VERSÃO CORRIGIDA
    public void onOkButtonClick(View view) {
        // Animação de clique - escurecer o botão
        animateButtonPress(view);
    }

    private void animateButtonPress(View button) {
        // Efeito de escurecimento
        button.animate()
                .alpha(0.6f)  // Escurece para 60% de opacidade
                .scaleX(0.95f)  // Diminui levemente
                .scaleY(0.95f)  // Diminui levemente
                .setDuration(100)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // Volta ao normal
                        button.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Processar o clique após a animação completar
                                        processButtonClick();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void processButtonClick() {
        // Parar o blinking quando o botão for clicado
        stopButtonBlinking();

        String userName = nameInput.getText().toString().trim();

        if (userName.isEmpty()) {
            shakeView(textInputLayout);
            // Reiniciar o blinking se houver erro
            startButtonBlinking();
            return;
        }

        // Animação de saída
        animateExitAndProceed(userName);
    }

    private void shakeView(View view) {
        ObjectAnimator shakeX = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f);
        shakeX.setDuration(500);
        shakeX.start();

        // Destacar campo em vermelho
        if (view instanceof TextInputLayout) {
            // Temporariamente mudar a cor da borda
            final TextInputLayout textLayout = (TextInputLayout) view;
            textLayout.setBoxStrokeErrorColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.RED));
            textLayout.setError("Por favor, digite seu nome");

            // Limpar erro após 3 segundos
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    textLayout.setError(null);
                    textLayout.setBoxStrokeErrorColor(null);
                }
            }, 3000);
        }
    }

    private void animateExitAndProceed(String userName) {
        // Animar saída de todos os elementos
        View[] views = {logo, titleText, welcomeText, infoText, nameLabel, textInputLayout, okButton};

        for (View view : views) {
            view.animate()
                    .alpha(0f)
                    .translationY(-100f)
                    .setDuration(400)
                    .start();
        }

        // Aguardar animação e prosseguir
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Aqui você pode navegar para a próxima atividade
                // Por exemplo:
                // Intent intent = new Intent(MainActivity.this, NextActivity.class);
                // intent.putExtra("USER_NAME", userName);
                // startActivity(intent);
                // finish();

                // Por enquanto, apenas mostrar um log
                android.util.Log.d("MainActivity", "Nome do usuário: " + userName);
            }
        }, 500);
    }

    private void stopButtonBlinking() {
        isButtonBlinking = false;
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        // Restaurar escala normal do botão
        okButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(100)
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopButtonBlinking();
    }
}
