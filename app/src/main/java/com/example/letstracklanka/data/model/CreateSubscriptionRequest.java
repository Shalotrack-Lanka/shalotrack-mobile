package com.example.letstracklanka.data.model;
import com.google.gson.annotations.SerializedName;
public class CreateSubscriptionRequest {

    // Matches the C# CreateSubscriptionDto.Plan field exactly --
    // "Free" | "OneYear" | "TwoYears" | "ThreeYears".

    @SerializedName(value = "plan", alternate = {"Plan"})
    private String plan;

    //constructor
    public CreateSubscriptionRequest(String plan){
        this.plan = plan;
    }
}
