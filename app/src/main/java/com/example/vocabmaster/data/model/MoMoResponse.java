package com.example.vocabmaster.data.model;

import com.google.gson.annotations.SerializedName;

public class MoMoResponse {
    @SerializedName("payUrl")
    private String payUrl;
    
    @SerializedName("resultCode")
    private int resultCode;

    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
}
