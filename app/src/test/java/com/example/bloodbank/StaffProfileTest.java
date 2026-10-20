package com.example.bloodbank;

import com.example.bloodbank.data.model.StaffProfile;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class StaffProfileTest {

    @Test
    public void testStaffUidPath() {
        StaffProfile profile = new StaffProfile("staff_uid_789", "Dr. Smith", "smith@bloodbank.org",
                "Red Cross", "9876543210", "Male", 1000L, 1000L);

        assertEquals("staff_uid_789", profile.getUid());
        String expectedPath = "/staffs/" + profile.getUid();
        assertEquals("/staffs/staff_uid_789", expectedPath);
    }

    @Test
    public void testStaffMapExcludesPrivilegedClaims() {
        StaffProfile profile = new StaffProfile("staff_uid_789", "Dr. Smith", "Smith@BloodBank.org ",
                "Red Cross", "9876543210", "Male", 1000L, 1000L);

        Map<String, Object> map = profile.toFirestoreMap();

        assertEquals("staff_uid_789", map.get("uid"));
        assertEquals("Dr. Smith", map.get("fullName"));
        assertEquals("Dr. Smith", map.get("name"));
        assertEquals("smith@bloodbank.org", map.get("email"));
        assertEquals("Red Cross", map.get("organisation"));
        assertEquals("Red Cross", map.get("org"));
        assertEquals("9876543210", map.get("phone"));
        assertEquals("9876543210", map.get("mobile"));
        assertEquals("Male", map.get("gender"));

        // Strictly forbidden authorization fields in Firestore documents
        assertFalse("Staff document must never contain 'role'", map.containsKey("role"));
        assertFalse("Staff document must never contain 'isStaff'", map.containsKey("isStaff"));
        assertFalse("Staff document must never contain 'admin'", map.containsKey("admin"));
    }
}
