package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Matches ShaloTrack_API.DTOs.Complaint.ComplaintReplyResponseDto.
 * authorType is a raw int matching ComplaintReplyAuthorType exactly:
 * Customer=0, Dealer=1, Admin=2 (no JsonStringEnumConverter on the API).
 */
public class ComplaintReplyResponse {

    @SerializedName(value = "complaintReplyId", alternate = {"ComplaintReplyId"})
    private String complaintReplyId;

    @SerializedName(value = "message", alternate = {"Message"})
    private String message;

    @SerializedName(value = "authorType", alternate = {"AuthorType"})
    private int authorType;

    @SerializedName(value = "authorName", alternate = {"AuthorName"})
    private String authorName;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    public String getComplaintReplyId() { return complaintReplyId; }
    public String getMessage() { return message; }
    public int getAuthorType() { return authorType; }
    public String getAuthorName() { return authorName; }
    public String getCreatedAt() { return createdAt; }

    /** true if this reply was written by the currently signed-in customer
     * themself — used purely for left/right bubble alignment in the
     * thread UI, not sent anywhere. */
    public boolean isFromCustomer() {
        return authorType == 0;
    }

    public String getAuthorLabel() {
        switch (authorType) {
            case 1: return "Dealer";
            case 2: return "ShaloTrack Support";
            default: return "You";
        }
    }
}