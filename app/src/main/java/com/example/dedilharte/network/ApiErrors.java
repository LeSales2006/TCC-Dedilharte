package com.example.dedilharte.network;

import org.json.JSONObject;
import retrofit2.Response;

public final class ApiErrors {
    private ApiErrors() {}

    public static String message(Response<?> response, String fallback) {
        if (response.code() == 401) return "Sua sessão expirou. Entre novamente.";
        if (response.code() == 403) return "Você não tem permissão para esta ação.";
        if (response.code() >= 500) return "O servidor não conseguiu concluir a operação. Tente novamente.";
        try {
            if (response.errorBody() != null) {
                String error = new JSONObject(response.errorBody().string()).optString("error", "");
                if (!error.trim().isEmpty()) return error;
            }
        } catch (Exception ignored) { }
        return fallback;
    }
}
