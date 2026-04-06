package com.example.vocabmaster.data.api;

import com.example.vocabmaster.data.model.PayOSRequest;
import com.example.vocabmaster.data.model.PayOSResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PayOSApiService {
    @POST("payments/create")
    Call<PayOSResponse> createPayment(@Body PayOSRequest request);
}
