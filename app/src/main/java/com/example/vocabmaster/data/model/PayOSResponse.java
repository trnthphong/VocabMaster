package com.example.vocabmaster.data.model;

import com.google.gson.annotations.SerializedName;

public class PayOSResponse {
    @SerializedName("checkoutUrl")
    private String checkoutUrl;
    
    @SerializedName("status")
    private String status;

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
