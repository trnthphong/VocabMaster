package com.example.vocabmaster.data.model;

import com.google.gson.annotations.SerializedName;

public class MoMoRequest {
    @SerializedName("userId")
    private String userId;
    
    @SerializedName("amount")
    private long amount;
    
    @SerializedName("orderInfo")
    private String orderInfo;

    public MoMoRequest(String userId, long amount, String orderInfo) {
        this.userId = userId;
        this.amount = amount;
        this.orderInfo = orderInfo;
    }

    // Getters and Setters
}
