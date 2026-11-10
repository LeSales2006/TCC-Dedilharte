package com.example.dedilharte.network;

import android.content.Context;

import com.example.dedilharte.auth.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class DedilharteApiClient {

    private static DedilharteApiService service;
    private static Context appContext;

    private DedilharteApiClient() {
    }

    public static void configure(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static synchronized DedilharteApiService service() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(75, TimeUnit.SECONDS)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        okhttp3.Request request = chain.request();
                        boolean publicAuth = request.url().encodedPath().equals("/api/auth/login")
                                || request.url().encodedPath().equals("/api/auth/register")
                                || request.url().encodedPath().equals("/api/auth/password-recovery/request")
                                || request.url().encodedPath().equals("/api/auth/password-recovery/verify")
                                || request.url().encodedPath().equals("/api/auth/password-recovery/reset");
                        if (!publicAuth && appContext != null && request.header("Authorization") == null) {
                            String token = new SessionManager(appContext).getToken();
                            if (token != null && !token.trim().isEmpty()) {
                                request = request.newBuilder()
                                        .header("Authorization", "Bearer " + token)
                                        .build();
                            }
                        }
                        return chain.proceed(request);
                    })
                    .addInterceptor(logging)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(ApiConfig.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(DedilharteApiService.class);
        }
        return service;
    }
}
