package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.DeleteAccountRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * The signed-in customer's own data rights (account deletion). No id in any route: the API acts on
 * the customer resolved from the token. These are the only endpoints that still answer while an
 * account is waiting for deletion.
 */
public interface AccountApi {

    /** Is deletion scheduled, and for when. data: { pending, requestedAt, scheduledFor, daysLeft, graceDays }. */
    @GET("api/Account/deletion")
    Call<ResponseBody> getDeletionStatus();

    /** Locks the account now and erases it in 30 days. Needs {"confirm":"DELETE"} and a sign-in from the last 10 minutes. */
    @POST("api/Account/delete")
    Call<ResponseBody> requestDeletion(@Body DeleteAccountRequest body);

    /** Withdraws a pending deletion (only before the permanent erasure). */
    @POST("api/Account/deletion/cancel")
    Call<ResponseBody> cancelDeletion();
}