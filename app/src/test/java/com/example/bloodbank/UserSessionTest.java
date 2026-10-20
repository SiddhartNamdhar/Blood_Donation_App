package com.example.bloodbank;

import com.example.bloodbank.data.model.DonorProfile;
import com.example.bloodbank.data.model.StaffProfile;
import com.example.bloodbank.data.model.UserSession;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserSessionTest {

    @Test
    public void testUnauthenticatedSession() {
        UserSession session = UserSession.unauthenticated();
        assertFalse(session.isAuthenticated());
        assertEquals(UserSession.Role.UNAUTHENTICATED, session.getRole());
        assertFalse(session.isVerifiedStaff());
        assertNull(session.getUid());
        assertNull(session.getEmail());
    }

    @Test
    public void testDonorSession() {
        DonorProfile donor = new DonorProfile(
                "donor_uid_1", "Alice Smith", "alice@example.com", "9876543210", 24,
                "Female", "O+", 1001, false, "PENDING_SCREENING", 1000L, 1000L
        );
        UserSession session = new UserSession(
                donor.getUid(), donor.getEmail(), UserSession.Role.DONOR, false, donor, null
        );

        assertTrue(session.isAuthenticated());
        assertEquals(UserSession.Role.DONOR, session.getRole());
        assertFalse(session.isVerifiedStaff());
        assertEquals("donor_uid_1", session.getUid());
        assertEquals("alice@example.com", session.getEmail());
        assertEquals("Alice Smith", session.getDisplayName());
    }

    @Test
    public void testVerifiedStaffSession() {
        StaffProfile staff = new StaffProfile(
                "staff_uid_1", "Dr. Bob", "bob@bloodbank.org", "City Blood Bank", "9876543210",
                "Male", 1000L, 1000L
        );
        UserSession session = new UserSession(
                staff.getUid(), staff.getEmail(), UserSession.Role.STAFF, true, null, staff
        );

        assertTrue(session.isAuthenticated());
        assertEquals(UserSession.Role.STAFF, session.getRole());
        assertTrue(session.isVerifiedStaff());
        assertEquals("staff_uid_1", session.getUid());
        assertEquals("bob@bloodbank.org", session.getEmail());
        assertEquals("Dr. Bob", session.getDisplayName());
    }
}
