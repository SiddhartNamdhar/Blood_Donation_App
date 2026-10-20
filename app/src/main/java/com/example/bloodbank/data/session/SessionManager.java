package com.example.bloodbank.data.session;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.bloodbank.data.model.DonorProfile;
import com.example.bloodbank.data.model.StaffProfile;

/**
 * Manages local session caching and retrieval.
 * Does not serve as the source of truth for authentication or authorization;
 * Firebase Authentication and Firestore remain the authoritative sources.
 */
public class SessionManager {

    private static final String PREF_NAME = "UserPrefs";

    public static final String KEY_USER_TYPE = "user_type"; // "Donor" or "Staff"
    public static final String KEY_USER_UID = "user_uid";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_NAME = "name";
    public static final String KEY_MOBILE = "mobile";
    public static final String KEY_AGE = "age";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_BLOOD_GROUP = "bloodGroup";
    public static final String KEY_DONOR_ID = "donor_id";
    public static final String KEY_ORG = "org";
    public static final String KEY_IS_STAFF_VERIFIED = "is_staff_verified";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveDonorSession(DonorProfile profile) {
        if (profile == null) return;
        prefs.edit()
                .putString(KEY_USER_TYPE, "Donor")
                .putString(KEY_USER_UID, profile.getUid())
                .putString(KEY_EMAIL, profile.getEmail())
                .putString(KEY_NAME, profile.getFullName())
                .putString(KEY_MOBILE, profile.getPhone())
                .putInt(KEY_AGE, profile.getAge())
                .putString(KEY_GENDER, profile.getGender())
                .putString(KEY_BLOOD_GROUP, profile.getBloodGroup())
                .putInt(KEY_DONOR_ID, profile.getDonorDisplayId())
                .putBoolean(KEY_IS_STAFF_VERIFIED, false)
                .apply();
    }

    public void saveStaffSession(StaffProfile profile, boolean isVerifiedStaff) {
        if (profile == null) return;
        prefs.edit()
                .putString(KEY_USER_TYPE, "Staff")
                .putString(KEY_USER_UID, profile.getUid())
                .putString(KEY_EMAIL, profile.getEmail())
                .putString(KEY_NAME, profile.getFullName())
                .putString(KEY_ORG, profile.getOrganisation())
                .putString(KEY_GENDER, profile.getGender())
                .putString(KEY_MOBILE, profile.getPhone())
                .putBoolean(KEY_IS_STAFF_VERIFIED, isVerifiedStaff)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        String uid = prefs.getString(KEY_USER_UID, null);
        return uid != null && !uid.trim().isEmpty();
    }

    public String getUserType() {
        return prefs.getString(KEY_USER_TYPE, "");
    }

    public boolean isStaff() {
        return "Staff".equalsIgnoreCase(getUserType()) && prefs.getBoolean(KEY_IS_STAFF_VERIFIED, false);
    }

    public boolean isDonor() {
        return "Donor".equalsIgnoreCase(getUserType());
    }

    public String getUid() {
        return prefs.getString(KEY_USER_UID, "");
    }

    public String getEmail(String fallback) {
        return prefs.getString(KEY_EMAIL, fallback != null ? fallback : "Not provided");
    }

    public String getName(String fallback) {
        return prefs.getString(KEY_NAME, fallback != null ? fallback : "Not provided");
    }

    public String getOrganisation(String fallback) {
        return prefs.getString(KEY_ORG, fallback != null ? fallback : "Not provided");
    }

    public String getMobile(String fallback) {
        return prefs.getString(KEY_MOBILE, fallback != null ? fallback : "Not provided");
    }

    public String getGender(String fallback) {
        return prefs.getString(KEY_GENDER, fallback != null ? fallback : "Not provided");
    }

    public String getBloodGroup(String fallback) {
        return prefs.getString(KEY_BLOOD_GROUP, fallback != null ? fallback : "Not provided");
    }

    public int getDonorDisplayId() {
        return prefs.getInt(KEY_DONOR_ID, 0);
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }
}
