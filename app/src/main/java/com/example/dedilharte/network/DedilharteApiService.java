package com.example.dedilharte.network;

import com.example.dedilharte.network.model.AuthResponse;
import com.example.dedilharte.network.model.ActivitySummaryResponse;
import com.example.dedilharte.network.model.ArpeggioListResponse;
import com.example.dedilharte.network.model.ArpeggioRequest;
import com.example.dedilharte.network.model.ArpeggioResponse;
import com.example.dedilharte.network.model.LoginRequest;
import com.example.dedilharte.network.model.LearningModuleListResponse;
import com.example.dedilharte.network.model.LearningModuleItemRequest;
import com.example.dedilharte.network.model.LearningModuleItemResponse;
import com.example.dedilharte.network.model.LearningModuleRequest;
import com.example.dedilharte.network.model.LearningModuleResponse;
import com.example.dedilharte.network.model.ManagedUserRequest;
import com.example.dedilharte.network.model.ModuleProgressRequest;
import com.example.dedilharte.network.model.ProgressRequest;
import com.example.dedilharte.network.model.ProgressResponse;
import com.example.dedilharte.network.model.ProgressListResponse;
import com.example.dedilharte.network.model.RegisterRequest;
import com.example.dedilharte.network.model.RoleUpdateRequest;
import com.example.dedilharte.network.model.SongProgressListResponse;
import com.example.dedilharte.network.model.SongProgressRequest;
import com.example.dedilharte.network.model.SongProgressResponse;
import com.example.dedilharte.network.model.StatusUpdateRequest;
import com.example.dedilharte.network.model.UserRequest;
import com.example.dedilharte.network.model.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import retrofit2.http.Path;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;

public interface DedilharteApiService {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @GET("api/auth/me")
    Call<AuthResponse> me();

    @GET("api/profile")
    Call<UserResponse> getProfile();

    @PUT("api/profile")
    Call<UserResponse> updateProfile(@Body UserRequest request);

    @GET("api/users/me")
    Call<UserResponse> getMe();

    @Multipart
    @PUT("api/users/me/photo")
    Call<UserResponse> uploadMyPhoto(@Part MultipartBody.Part photo);

    @GET("api/users/me/photo")
    Call<ResponseBody> getMyPhoto();

    @DELETE("api/users/me/photo")
    Call<Void> deleteMyPhoto();

    @POST("api/activity")
    Call<Void> recordActivity();

    @GET("api/activity/summary")
    Call<ActivitySummaryResponse> getActivitySummary();

    @GET("api/songs/progress")
    Call<SongProgressListResponse> getSongProgress();

    @POST("api/songs/progress")
    Call<SongProgressResponse> upsertSongProgress(@Body SongProgressRequest request);

    @GET("api/arpeggios")
    Call<ArpeggioListResponse> getArpeggios();

    @GET("api/admin/arpeggios")
    Call<ArpeggioListResponse> getAdminArpeggios();

    @POST("api/admin/arpeggios")
    Call<ArpeggioResponse> createArpeggio(@Body ArpeggioRequest request);

    @PUT("api/admin/arpeggios/{id}")
    Call<ArpeggioResponse> updateArpeggio(@Path("id") String id, @Body ArpeggioRequest request);

    @DELETE("api/admin/arpeggios/{id}")
    Call<Void> deleteArpeggio(@Path("id") String id);

    @GET("api/modules")
    Call<LearningModuleListResponse> getModules();

    @GET("api/staff/modules")
    Call<LearningModuleListResponse> getStaffModules();

    @POST("api/configurator/modules")
    Call<LearningModuleResponse> createModule(@Body LearningModuleRequest request);

    @POST("api/staff/modules/{id}/items")
    Call<LearningModuleItemResponse> createModuleItem(@Path("id") String moduleId, @Body LearningModuleItemRequest request);

    @PATCH("api/staff/module-items/{id}")
    Call<LearningModuleItemResponse> updateModuleItem(@Path("id") String itemId, @Body LearningModuleItemRequest request);

    @PATCH("api/staff/module-items/{id}/order")
    Call<LearningModuleItemResponse> updateModuleItemOrder(@Path("id") String itemId, @Body LearningModuleItemRequest request);

    @DELETE("api/staff/module-items/{id}")
    Call<Void> deleteModuleItem(@Path("id") String itemId);

    @POST("api/module-items/{id}/progress")
    Call<Void> upsertModuleProgress(@Path("id") String itemId, @Body ModuleProgressRequest request);

    @POST("api/configurator/users")
    Call<UserResponse> createManagedUser(@Body ManagedUserRequest request);

    @PATCH("api/configurator/users/{id}/role")
    Call<UserResponse> updateManagedRole(@Path("id") String id, @Body RoleUpdateRequest request);

    @PATCH("api/configurator/users/{id}/status")
    Call<UserResponse> updateManagedStatus(@Path("id") String id, @Body StatusUpdateRequest request);

    @DELETE("api/configurator/users/{id}")
    Call<Void> deleteManagedUser(@Path("id") String id);

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
