package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * One renewal package as returned by GET api/Renewals/packages. The server owns the price list; this
 * screen only displays it. Only packages that are active and priced are ever returned, and no
 * dealer/distributor margin is ever part of it.
 */
public class RenewalPackage {

    /** The value to send as the renewal duration (ThreeMonths ... SixYears). */
    @SerializedName("duration")
    private String duration;
    @SerializedName("label")
    private String label;
    @SerializedName("positioning")
    private String positioning;
    @SerializedName("months")
    private Integer months;
    @SerializedName("priceLkr")
    private Double priceLkr;
    @SerializedName("warrantyMonths")
    private Integer warrantyMonths;

    public String getDuration() { return duration; }
    public String getLabel() { return label == null ? "" : label; }
    public String getPositioning() { return positioning; }
    public int getMonths() { return months == null ? 0 : months; }
    public Double getPriceLkr() { return priceLkr; }
    public int getWarrantyMonths() { return warrantyMonths == null ? 0 : warrantyMonths; }

    /** A package without a duration or a positive price cannot be ordered, so it is never shown. */
    public boolean isOrderable() {
        return duration != null && !duration.trim().isEmpty() && priceLkr != null && priceLkr > 0;
    }
}