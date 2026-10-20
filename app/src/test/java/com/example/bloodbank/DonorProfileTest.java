package com.example.bloodbank;

import com.example.bloodbank.data.model.DonorProfile;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class DonorProfileTest {

    @Test
    public void testUidBasedDocumentPath() {
        String uid = "donor_test_uid_456";
        DonorProfile profile = new DonorProfile(
                uid, "Jane Doe", "jane@example.com", "9876543210", 25,
                "Female", "O+", 1234, false, "PENDING_SCREENING", 1000L, 1000L
        );

        assertEquals("donor_test_uid_456", profile.getUid());
        String expectedFirestorePath = "/donors/" + profile.getUid();
        assertEquals("/donors/donor_test_uid_456", expectedFirestorePath);
    }

    @Test
    public void testRegistrationMapContainsRequiredFields() {
        DonorProfile profile = new DonorProfile(
                "uid123", "Jane Doe", "Jane@Example.COM  ", "9876543210", 28,
                "Female", "B+", 5678, false, "PENDING_SCREENING", 2000L, 2000L
        );

        Map<String, Object> map = profile.toFirestoreRegistrationMap();

        assertEquals("uid123", map.get("uid"));
        assertEquals("Jane Doe", map.get("fullName"));
        assertEquals("Jane Doe", map.get("name")); // legacy compatibility
        assertEquals("jane@example.com", map.get("email")); // normalized
        assertEquals("9876543210", map.get("phone"));
        assertEquals("9876543210", map.get("mobile")); // legacy compatibility
        assertEquals(28, map.get("age"));
        assertEquals("Female", map.get("gender"));
        assertEquals("B+", map.get("bloodGroup"));
        assertEquals(5678, map.get("donorDisplayId"));
        assertFalse("Registration map must not contain lookupToken", map.containsKey("lookupToken"));
        assertEquals(false, map.get("optInNotifications"));
        assertEquals(2000L, map.get("createdAt"));
        assertEquals(2000L, map.get("updatedAt"));
    }

    @Test
    public void testPreventsPrivilegeEscalationInRegistrationMap() {
        DonorProfile profile = new DonorProfile(
                "uid_attacker", "Attacker", "hacker@test.com", "9876543210", 30,
                "Male", "AB+", 9999, true, "CLEARED", 3000L, 3000L
        );

        Map<String, Object> map = profile.toFirestoreRegistrationMap();

        // Ensure privileged fields that violate firestore.rules are NOT present
        assertFalse("Registration map must not contain 'role'", map.containsKey("role"));
        assertFalse("Registration map must not contain 'screeningStatus'", map.containsKey("screeningStatus"));
        assertFalse("Registration map must not contain 'isStaff'", map.containsKey("isStaff"));
        assertFalse("Registration map must not contain 'admin'", map.containsKey("admin"));
        assertFalse("Registration map must not contain 'lookupToken'", map.containsKey("lookupToken"));
    }

    @Test
    public void testEmailNormalization() {
        DonorProfile profile = new DonorProfile();
        profile.setEmail("  USER.Test@Example.COM  ");
        assertEquals("user.test@example.com", profile.getEmail());
    }
}
