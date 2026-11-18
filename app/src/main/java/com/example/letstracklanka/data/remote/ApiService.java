package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateEmergencyContactRequest;
import com.example.letstracklanka.data.model.CreateSubscriptionRequest;
import com.example.letstracklanka.data.model.CreateSavedPlaceRequest;
import com.example.letstracklanka.data.model.CreateGeofenceRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.InviteVehicleShareRequest;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.model.RespondToVehicleShareRequest;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.model.UpdateGeofenceRequest;
import com.example.letstracklanka.data.model.UpdateVehicleRequest;
import com.example.letstracklanka.data.model.CreateComplaintReplyRequest;
import com.example.letstracklanka.data.model.CreateComplaintRequest;

import com.example.letstracklanka.data.model.CreateRenewalRequest;

import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);

    @GET("api/Customers/me")
    Call<ResponseBody> getMyProfile();

    @PUT("api/Customers/{customerId}")
    Call<ResponseBody> updateCustomer(@Path("customerId") String customerId, @Body UpdateCustomerRequest request);

    @GET("api/Vehicles/customer/{customerId}")
    Call<ResponseBody> getVehiclesByCustomer(@Path("customerId") String customerId);

    // The vehicle endpoints answer in the ApiResponse envelope ({ success, message, data: { ...vehicle } }), so
    // they are read as raw bodies and the "data" object is parsed with VehicleResponse.from(...). Typing them as
    // Call<VehicleResponse> parsed the envelope itself: every field came back null (no vehicle ID after create,
    // so the tracker was never linked; an empty details card and a broken Edit on the vehicle page).
    @POST("api/Vehicles")
    Call<ResponseBody> createVehicle(@Body CreateVehicleRequest request);

    @PUT("api/Vehicles/{vehicleId}")
    Call<ResponseBody> updateVehicle(@Path("vehicleId") String vehicleId, @Body UpdateVehicleRequest request);

    // Works for owned and shared vehicles (the server checks access); the list endpoint only returns owned ones.
    @GET("api/Vehicles/{vehicleId}")
    Call<ResponseBody> getVehicleById(@Path("vehicleId") String vehicleId);

    // NEW -- Geofencing.
    @GET("api/Geofences")
    Call<ResponseBody> getMyGeofences();

    @POST("api/Geofences")
    Call<ResponseBody> addGeofence(@Body CreateGeofenceRequest request);

    @PUT("api/Geofences/{geofenceId}")
    Call<ResponseBody> updateGeofence(@Path("geofenceId") String geofenceId, @Body UpdateGeofenceRequest request);

    @DELETE("api/Geofences/{geofenceId}")
    Call<ResponseBody> deleteGeofence(@Path("geofenceId") String geofenceId);

    @POST("api/DeviceAssignments/assign")
    Call<ResponseBody> assignDevice(@Body CreateDeviceAssignmentRequest request);

    // Assignment history of one vehicle; the active row (Status 1, no RemovedAt) carries the
    // assignmentId that unassign needs.
    @GET("api/DeviceAssignments/vehicle/{vehicleId}")
    Call<ResponseBody> getDeviceAssignmentsForVehicle(@Path("vehicleId") String vehicleId);

    // Owner-only on the server. Keeps the subscription on the device.
    @PATCH("api/DeviceAssignments/{assignmentId}/unassign")
    Call<ResponseBody> unassignDevice(@Path("assignmentId") String assignmentId);

    @GET("api/Customers/{customerId}/dashboard")
    Call<ResponseBody> getCustomerDashboard(@Path("customerId") String customerId);

    @GET("api/GpsDevices/lookup/{imei}")
    Call<ResponseBody> lookupDeviceByImei(@Path("imei") String imei);

    // NEW: vehicleId is optional -- pass null for the existing "all my
    // vehicles" behavior, or a real ID to filter to one vehicle. Retrofit
    // omits the query parameter entirely from the URL when it's null,
    // rather than sending "vehicleId=null" literally.
    @GET("api/Alerts")
    Call<ResponseBody> getMyAlerts(@Query("page") int page, @Query("pageSize") int pageSize, @Query("vehicleId") String vehicleId);

    @PATCH("api/Alerts/{alertId}/read")
    Call<ResponseBody> markAlertAsRead(@Path("alertId") long alertId);

    // Unread count for the Alerts badge: one COUNT on the server (data is a number).
    @GET("api/Alerts/unread-count")
    Call<ResponseBody> getUnreadAlertCount();

    // Marks every unread alert read; vehicleId null = all the customer's vehicles. data = how many changed.
    @PATCH("api/Alerts/read-all")
    Call<ResponseBody> markAllAlertsRead(@Query("vehicleId") String vehicleId);

    // NEW — was built on the API side during Alerts Stage 1 but never actually
    // wired on the mobile side until now.
    @POST("api/Alerts/register-token")
    Call<ResponseBody> registerFcmToken(@Body RegisterFcmTokenRequest request);

    // Sign-out: stops this phone receiving the customer's alerts. Server deletes the token only if it
    // belongs to the caller. POST because the token travels in the body.
    @POST("api/Alerts/unregister-token")
    Call<ResponseBody> unregisterFcmToken(@Body RegisterFcmTokenRequest request);

    // NEW — soft-deletes a vehicle (sets IsActive=false, frees its device's
    // IMEI for reassignment). See VehicleService.DeleteAsync() on the API side.
    @DELETE("api/Vehicles/{vehicleId}")
    Call<ResponseBody> deleteVehicle(@Path("vehicleId") String vehicleId);

    @POST("api/Subscriptions")
    Call<ResponseBody> requestSubscription(@Body CreateSubscriptionRequest request);

    // This customer's app plans, newest first (More > App plan shows the active or pending one).
    @GET("api/Subscriptions")
    Call<ResponseBody> getMySubscriptions();

    // NEW — Emergency Contacts (EmergencyContactsActivity). Plan-based limit
    // is enforced server-side in EmergencyContactService.AddContactAsync;
    // a 403 here means the limit was hit, surfaced via the real error
    // message on the Android side, not a generic failure.
    @GET("api/EmergencyContacts")
    Call<ResponseBody> getMyEmergencyContacts();

    @POST("api/EmergencyContacts")
    Call<ResponseBody> addEmergencyContact(@Body CreateEmergencyContactRequest request);

    @DELETE("api/EmergencyContacts/{emergencyContactId}")
    Call<ResponseBody> deleteEmergencyContact(@Path("emergencyContactId") String emergencyContactId);

    // NEW -- matches the real SOSController route confirmed working
    // server-side (POST /api/SOS/{vehicleId}/trigger). No request body;
    // location is resolved server-side from CurrentLocations, not sent
    // from the client.
    @POST("api/SOS/{vehicleId}/trigger")
    Call<ResponseBody> triggerSOS(@Path("vehicleId") String vehicleId);

    // NEW -- matches the real SavedPlacesController routes. GET returns
    // places already sorted by VisitCount descending server-side.
    @GET("api/SavedPlaces")
    Call<ResponseBody> getMyPlaces();

    @POST("api/SavedPlaces")
    Call<ResponseBody> addPlace(@Body CreateSavedPlaceRequest request);

    @DELETE("api/SavedPlaces/{placeId}")
    Call<ResponseBody> deletePlace(@Path("placeId") String placeId);

    // NEW -- matches the real VehicleStatsController route.
    @GET("api/VehicleStats/{vehicleId}")
    // period: "today" | "week" | "month" | "all"
    Call<ResponseBody> getVehicleStats(@Path("vehicleId") String vehicleId, @Query("period") String period);

    // NEW -- Vehicle Sharing. Matches the real VehicleSharesController routes.
    @POST("api/VehicleShares/invite")
    Call<ResponseBody> inviteVehicleShare(@Body InviteVehicleShareRequest request);

    @POST("api/VehicleShares/{shareId}/respond")
    Call<ResponseBody> respondToVehicleShare(@Path("shareId") String shareId, @Body RespondToVehicleShareRequest request);

    @DELETE("api/VehicleShares/{shareId}")
    Call<ResponseBody> revokeVehicleShare(@Path("shareId") String shareId);

    @GET("api/VehicleShares/my-shares")
    Call<ResponseBody> getMyVehicleShares(@Query("vehicleId") String vehicleId);

    @GET("api/VehicleShares/shared-with-me")
    Call<ResponseBody> getVehiclesSharedWithMe();

    @GET("api/VehicleShares/pending-invites")
    Call<ResponseBody> getPendingVehicleShareInvites();

    // NEW -- Complaints/Feedback. Matches the real ComplaintsController
// routes exactly (api/Complaints, [Authorize]). Category/Status/
// AuthorType all serialize as raw ints (no JsonStringEnumConverter
// configured on the API), same constraint already handled elsewhere
// in this project -- resolved to labels on this side, in
// ComplaintResponse itself, not re-derived per screen.
    @POST("api/Complaints")
    Call<ResponseBody> fileComplaint(@Body CreateComplaintRequest request);

    @GET("api/Complaints/mine")
    Call<ResponseBody> getMyComplaints();

    @GET("api/Complaints/{complaintId}")
    Call<ResponseBody> getComplaintById(@Path("complaintId") String complaintId);

    @POST("api/Complaints/{complaintId}/reply")
    Call<ResponseBody> replyToComplaint(@Path("complaintId") String complaintId, @Body CreateComplaintReplyRequest request);

    // NEW -- report generation feature (Alert Report card). Distinct from
    // getMyAlerts above: requires a specific vehicle + bounded date range,
    // returns a per-type count summary alongside the full list.
    @GET("api/Alerts/report")
    Call<ResponseBody> getAlertReport(@Query("vehicleId") String vehicleId, @Query("from") String fromIso, @Query("to") String toIso);

    // NEW -- report generation feature (KM Report): explicit date range
    // instead of a period preset. Separate method rather than adding
    // from/to params to getVehicleStats above -- that one's callers
    // (ValueActivity's period buttons) never pass a range, and Retrofit
    // omits a null @Query param cleanly, but a distinct method keeps the
    // "preset" and "report" call shapes from being silently conflatable.
    @GET("api/VehicleStats/{vehicleId}")
    Call<ResponseBody> getVehicleStatsForRange(@Path("vehicleId") String vehicleId, @Query("from") String fromIso, @Query("to") String toIso);

    // NEW -- Customer renewals (RenewalsController, api/Renewals, [Authorize]). Unlike the complaint
    // endpoints these use enum NAMES as strings. The slip part must be named "file"; the server checks
    // type by magic bytes and caps size at 2 MB (SlipPreparer already shrinks photos to fit).
    // The renewal price list (active, priced packages only). Customer-safe: no margins.
    @GET("api/Renewals/packages")
    Call<ResponseBody> getRenewalPackages();

    @POST("api/Renewals")
    Call<ResponseBody> createRenewal(@Body CreateRenewalRequest request);

    @Multipart
    @POST("api/Renewals/{renewalRequestId}/slip")
    Call<ResponseBody> uploadRenewalSlip(@Path("renewalRequestId") String renewalRequestId, @Part MultipartBody.Part file);

    @GET("api/Renewals")
    Call<ResponseBody> getMyRenewals();

    @PATCH("api/Renewals/{renewalRequestId}/cancel")
    Call<ResponseBody> cancelRenewal(@Path("renewalRequestId") String renewalRequestId);

    // ── Vehicle page (Drop 2). Owner-only on the server; anyone else gets 404. ──

    // Live location link: body {"durationHours": 1..24}. The token is in this reply only.
    @POST("api/LiveShare/vehicle/{vehicleId}")
    Call<ResponseBody> createLiveShare(@Path("vehicleId") String vehicleId, @Body JsonObject body);

    @GET("api/LiveShare/vehicle/{vehicleId}")
    Call<ResponseBody> getLiveShares(@Path("vehicleId") String vehicleId);

    @DELETE("api/LiveShare/{linkId}")
    Call<ResponseBody> revokeLiveShare(@Path("linkId") String linkId);

    @GET("api/VehicleAlertSettings/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleAlertSettings(@Path("vehicleId") String vehicleId);

    // body {"speedLimitKmh", "idleAlertEnabled", "idleAlertMinutes"}
    @PUT("api/VehicleAlertSettings/vehicle/{vehicleId}")
    Call<ResponseBody> updateVehicleAlertSettings(@Path("vehicleId") String vehicleId, @Body JsonObject body);

    @GET("api/VehicleReminders/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleReminders(@Path("vehicleId") String vehicleId);

    // body {"type": 0 licence | 1 insurance | 2 service, "dueDate": "yyyy-MM-dd", "notes"}
    @PUT("api/VehicleReminders/vehicle/{vehicleId}")
    Call<ResponseBody> upsertVehicleReminder(@Path("vehicleId") String vehicleId, @Body JsonObject body);

    @DELETE("api/VehicleReminders/{reminderId}")
    Call<ResponseBody> deleteVehicleReminder(@Path("reminderId") String reminderId);

    // ── Account (Drop 2) ──

    @GET("api/WeeklySummary/settings")
    Call<ResponseBody> getWeeklySummarySettings();

    // body {"enabled": true|false}
    @PUT("api/WeeklySummary/settings")
    Call<ResponseBody> updateWeeklySummarySettings(@Body JsonObject body);

    // Download my data: the envelope's "data" is the whole export. Rate limited on the server.
    @GET("api/Account/export")
    Call<ResponseBody> exportMyData();

    // Change the sign-in number. Needs a sign-in in the last 10 minutes (403 REAUTH_REQUIRED otherwise).
    @POST("api/Customers/me/phone-change/request")
    Call<ResponseBody> requestPhoneChange(@Body JsonObject body);

    @POST("api/Customers/me/phone-change/verify")
    Call<ResponseBody> verifyPhoneChange(@Body JsonObject body);
}