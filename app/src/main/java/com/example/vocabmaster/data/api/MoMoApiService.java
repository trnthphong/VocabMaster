package com.example.vocabmaster.data.api;

import com.example.vocabmaster.data.model.MoMoRequest;
import com.example.vocabmaster.data.model.MoMoResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MoMoApiService {
    // Sử dụng đường dẫn tương đối và thêm /api/ để khớp với backend
    @POST("api/payments/momo/create")
    Call<MoMoResponse> createPayment(@Body MoMoRequest request);
}
