package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class CreateComplaintReplyRequest {

    @SerializedName("message")
    private String message;

    public CreateComplaintReplyRequest(String message) {
        this.message = message;
    }
}