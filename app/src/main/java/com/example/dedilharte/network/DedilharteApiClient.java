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

    public static DedilharteApiService service() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        okhttp3.Request request = chain.request();
                        if (appContext != null && request.header("Authorization") == null) {
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
