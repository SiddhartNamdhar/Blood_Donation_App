package com.example.bloodbank.data.model;

/**
 * Encapsulates the active authenticated user session in the application.
 */
public class UserSession {

    public enum Role {
        DONOR,
        STAFF,
        UNAUTHENTICATED
    }

    private final String uid;
    private final String email;
    private final Role role;
    private final boolean isVerifiedStaff;
    private final DonorProfile donorProfile;
    private final StaffProfile staffProfile;

    public UserSession(String uid, String email, Role role, boolean isVerifiedStaff,
                       DonorProfile donorProfile, StaffProfile staffProfile) {
        this.uid = uid;
        this.email = email;
        this.role = role;
        this.isVerifiedStaff = isVerifiedStaff;
        this.donorProfile = donorProfile;
        this.staffProfile = staffProfile;
    }

    public static UserSession unauthenticated() {
        return new UserSession(null, null, Role.UNAUTHENTICATED, false, null, null);
    }

    public boolean isAuthenticated() {
        return uid != null && !uid.trim().isEmpty() && role != Role.UNAUTHENTICATED;
    }

    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public boolean isVerifiedStaff() { return isVerifiedStaff; }
    public DonorProfile getDonorProfile() { return donorProfile; }
    public StaffProfile getStaffProfile() { return staffProfile; }

    public String getDisplayName() {
        if (donorProfile != null && donorProfile.getFullName() != null && !donorProfile.getFullName().isEmpty()) {
            return donorProfile.getFullName();
        }
        if (staffProfile != null && staffProfile.getFullName() != null && !staffProfile.getFullName().isEmpty()) {
            return staffProfile.getFullName();
        }
        return email != null ? email : "User";
    }
}
