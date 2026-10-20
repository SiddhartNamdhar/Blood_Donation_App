package com.example.bloodbank.data.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data model representing a Donor Profile stored in Firestore at /donors/{uid}.
 * Does not contain passwords, lookup tokens, or client-assignable privileged authorization fields.
 */
public class DonorProfile {

    private String uid;
    private String fullName;
    private String email;
    private String phone;
    private int age;
    private String gender;
    private String bloodGroup;
    private int donorDisplayId;
    private boolean optInNotifications;
    private String screeningStatus;
    private long createdAt;
    private long updatedAt;

    public DonorProfile() {
        // Required for Firestore deserialization
    }

    public DonorProfile(String uid, String fullName, String email, String phone, int age,
                        String gender, String bloodGroup, int donorDisplayId,
                        boolean optInNotifications, String screeningStatus, long createdAt, long updatedAt) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
        this.phone = phone != null ? phone.trim() : "";
        this.age = age;
        this.gender = gender != null ? gender : "Other";
        this.bloodGroup = bloodGroup != null ? bloodGroup : "O+";
        this.donorDisplayId = donorDisplayId;
        this.optInNotifications = optInNotifications;
        this.screeningStatus = screeningStatus != null ? screeningStatus : "PENDING_SCREENING";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    /**
     * Map for Firestore initial creation by client.
     * EXCLUDES privileged fields: role, screeningStatus, isStaff, admin.
     * EXCLUDES removed lookupToken.
     */
    public Map<String, Object> toFirestoreRegistrationMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("fullName", fullName);
        map.put("name", fullName); // backward compatibility with existing views
        map.put("email", email != null ? email.trim().toLowerCase(Locale.ROOT) : "");
        map.put("phone", phone);
        map.put("mobile", phone); // backward compatibility with existing views
        map.put("age", age);
        map.put("gender", gender);
        map.put("bloodGroup", bloodGroup);
        map.put("donorDisplayId", donorDisplayId);
        map.put("optInNotifications", optInNotifications);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    /**
     * Converts a Firestore DocumentSnapshot into a typed DonorProfile.
     * Ignores legacy lookupToken if present in existing production documents.
     */
    public static DonorProfile fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        String uid = doc.getId();
        String fullName = doc.getString("fullName");
        if (fullName == null || fullName.isEmpty()) {
            fullName = doc.getString("name");
        }
        if (fullName == null) {
            fullName = "Valued Donor";
        }

        String email = doc.getString("email");
        if (email == null) email = "";

        String phone = doc.getString("phone");
        if (phone == null || phone.isEmpty()) {
            phone = doc.getString("mobile");
        }
        if (phone == null) phone = "";

        Long ageLong = doc.getLong("age");
        int age = ageLong != null ? ageLong.intValue() : 0;

        String gender = doc.getString("gender");
        if (gender == null) gender = "Not provided";

        String bloodGroup = doc.getString("bloodGroup");
        if (bloodGroup == null) bloodGroup = "O+";

        Long displayIdLong = doc.getLong("donorDisplayId");
        int displayId = displayIdLong != null ? displayIdLong.intValue() : 0;

        Boolean optIn = doc.getBoolean("optInNotifications");
        boolean optInNotifications = Boolean.TRUE.equals(optIn);

        String screeningStatus = doc.getString("screeningStatus");
        if (screeningStatus == null) screeningStatus = "PENDING_SCREENING";

        Long createdLong = doc.getLong("createdAt");
        long createdAt = createdLong != null ? createdLong : System.currentTimeMillis();

        Long updatedLong = doc.getLong("updatedAt");
        long updatedAt = updatedLong != null ? updatedLong : System.currentTimeMillis();

        return new DonorProfile(uid, fullName, email, phone, age, gender, bloodGroup,
                displayId, optInNotifications, screeningStatus, createdAt, updatedAt);
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase(Locale.ROOT) : ""; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getDonorDisplayId() { return donorDisplayId; }
    public void setDonorDisplayId(int donorDisplayId) { this.donorDisplayId = donorDisplayId; }

    public boolean isOptInNotifications() { return optInNotifications; }
    public void setOptInNotifications(boolean optInNotifications) { this.optInNotifications = optInNotifications; }

    public String getScreeningStatus() { return screeningStatus; }
    public void setScreeningStatus(String screeningStatus) { this.screeningStatus = screeningStatus; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
