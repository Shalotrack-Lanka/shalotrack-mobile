package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/** Body of POST api/Account/delete. The API requires the exact word DELETE. */
public class DeleteAccountRequest {
    @SerializedName("confirm")
    private final String confirm;

    public DeleteAccountRequest(String confirm) {
        this.confirm = confirm;
    }

    public String getConfirm() {
        return confirm;
    }
}