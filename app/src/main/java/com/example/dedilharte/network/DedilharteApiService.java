package com.example.dedilharte.network;

import com.example.dedilharte.network.model.ProgressRequest;
import com.example.dedilharte.network.model.ProgressResponse;
import com.example.dedilharte.network.model.ProgressListResponse;
import com.example.dedilharte.network.model.UserRequest;
import com.example.dedilharte.network.model.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface DedilharteApiService {

    @POST("api/users")
    Call<UserResponse> upsertUser(@Body UserRequest request);

    @GET("api/users/{id}")
    Call<UserResponse> getUser(@Path("id") String id);

    @PUT("api/users/{id}")
    Call<UserResponse> updateUser(@Path("id") String id, @Body UserRequest request);

    @DELETE("api/users/{id}")
    Call<Void> deleteUser(@Path("id") String id);

    @GET("api/users/{userId}/progress")
    Call<ProgressListResponse> getProgress(@Path("userId") String userId);

    @POST("api/users/{userId}/progress")
    Call<ProgressResponse> upsertProgress(
            @Path("userId") String userId,
            @Body ProgressRequest request
    );
}
