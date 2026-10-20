package com.example.bloodbank.data.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data model representing a Staff Profile stored in Firestore at /staffs/{uid}.
 * Strictly does not contain privileged authorization fields ('role', 'isStaff', 'admin')
 * to enforce security rules and prevent client-side privilege escalation.
 */
public class StaffProfile {

    private String uid;
    private String fullName;
    private String email;
    private String organisation;
    private String phone;
    private String gender;
    private long createdAt;
    private long updatedAt;

    public StaffProfile() {
        // Required for Firestore deserialization
    }

    public StaffProfile(String uid, String fullName, String email, String organisation,
                        String phone, String gender, long createdAt, long updatedAt) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
        this.organisation = organisation != null ? organisation.trim() : "Blood Bank Staff";
        this.phone = phone != null ? phone.trim() : "";
        this.gender = gender != null ? gender : "Not provided";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    /**
     * Map for Firestore creation and update.
     * EXCLUDES privileged authorization fields: role, isStaff, admin.
     */
    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("fullName", fullName);
        map.put("name", fullName); // backward compatibility with existing views
        map.put("email", email != null ? email.trim().toLowerCase(Locale.ROOT) : "");
        map.put("organisation", organisation);
        map.put("org", organisation); // backward compatibility with existing views
        map.put("phone", phone);
        map.put("mobile", phone); // backward compatibility with existing views
        map.put("gender", gender);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    /**
     * Converts a Firestore DocumentSnapshot into a typed StaffProfile.
     */
    public static StaffProfile fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        String uid = doc.getId();
        String fullName = doc.getString("fullName");
        if (fullName == null || fullName.isEmpty()) {
            fullName = doc.getString("name");
        }
        if (fullName == null) {
            fullName = "Staff Member";
        }

        String email = doc.getString("email");
        if (email == null) email = "";

        String organisation = doc.getString("organisation");
        if (organisation == null || organisation.isEmpty()) {
            organisation = doc.getString("org");
        }
        if (organisation == null) organisation = "Not provided";

        String phone = doc.getString("phone");
        if (phone == null || phone.isEmpty()) {
            phone = doc.getString("mobile");
        }
        if (phone == null) phone = "Not provided";

        String gender = doc.getString("gender");
        if (gender == null) gender = "Not provided";

        Long createdLong = doc.getLong("createdAt");
        long createdAt = createdLong != null ? createdLong : System.currentTimeMillis();

        Long updatedLong = doc.getLong("updatedAt");
        long updatedAt = updatedLong != null ? updatedLong : System.currentTimeMillis();

        return new StaffProfile(uid, fullName, email, organisation, phone, gender, createdAt, updatedAt);
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase(Locale.ROOT) : ""; }

    public String getOrganisation() { return organisation; }
    public void setOrganisation(String organisation) { this.organisation = organisation; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
