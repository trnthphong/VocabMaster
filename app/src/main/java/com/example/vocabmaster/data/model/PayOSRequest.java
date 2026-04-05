package com.example.vocabmaster.data.model;

import com.google.gson.annotations.SerializedName;

public class PayOSRequest {
    @SerializedName("userId")
    private String userId;
    
    @SerializedName("amount")
    private int amount;
    
    @SerializedName("description")
    private String description;

    public PayOSRequest(String userId, int amount, String description) {
        this.userId = userId;
        this.amount = amount;
        this.description = description;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
