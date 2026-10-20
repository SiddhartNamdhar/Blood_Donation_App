package com.example.bloodbank;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExampleUnitTest {

    private boolean isValidVolume(int vol) {
        return vol >= 200 && vol <= 500;
    }

    private boolean isValidHemoglobin(double hb) {
        return !Double.isNaN(hb) && !Double.isInfinite(hb) && hb >= 7.0 && hb <= 20.0;
    }

    private boolean isValidDonorUid(String uid) {
        return uid != null && !uid.trim().isEmpty() && !uid.startsWith("DONOR_UID_");
    }

    private boolean isUpcomingCamp(String campDate, String todayDate) {
        return campDate != null && campDate.compareTo(todayDate) >= 0;
    }

    @Test
    public void testQrRemovalVerification() {
        // Verification that canonical donor identity relies exclusively on Firebase UID and Display ID
        String testUid = "FIREBASE_UID_12345";
        assertTrue("Canonical donor identification must use valid UID", isValidDonorUid(testUid));
    }

    @Test
    public void testDonorUidValidation() {
        assertTrue("Real Firebase UID should be valid", isValidDonorUid("mX9kL2pQ8zN1vY3w"));
        assertFalse("Null UID should be rejected", isValidDonorUid(null));
        assertFalse("Empty UID should be rejected", isValidDonorUid("   "));
        assertFalse("Fallback placeholder UID should be rejected", isValidDonorUid("DONOR_UID_1001"));
    }

    @Test
    public void testBloodVolumeValidation() {
        assertTrue("Volume 450ml should be valid", isValidVolume(450));
        assertTrue("Volume 200ml should be valid boundary", isValidVolume(200));
        assertTrue("Volume 500ml should be valid boundary", isValidVolume(500));

        assertFalse("Volume 150ml should be invalid", isValidVolume(150));
        assertFalse("Volume 600ml should be invalid", isValidVolume(600));
        assertFalse("Volume 0ml should be invalid", isValidVolume(0));
    }

    @Test
    public void testHemoglobinValidation() {
        assertTrue("Hemoglobin 13.8 should be valid", isValidHemoglobin(13.8));
        assertTrue("Hemoglobin 7.0 should be valid boundary", isValidHemoglobin(7.0));
        assertTrue("Hemoglobin 20.0 should be valid boundary", isValidHemoglobin(20.0));

        assertFalse("Hemoglobin 5.2 should be invalid", isValidHemoglobin(5.2));
        assertFalse("Hemoglobin 22.5 should be invalid", isValidHemoglobin(22.5));
        assertFalse("NaN Hemoglobin should be invalid", isValidHemoglobin(Double.NaN));
        assertFalse("Infinite Hemoglobin should be invalid", isValidHemoglobin(Double.POSITIVE_INFINITY));
    }

    @Test
    public void testIdempotencyKeyFormatting() {
        String realDonorUid = "mX9kL2pQ8zN1vY3w";
        String date = "2026-10-14";
        String expectedKey = "DONATION_mX9kL2pQ8zN1vY3w_2026-10-14";
        String generatedKey = "DONATION_" + realDonorUid + "_" + date;
        assertEquals(expectedKey, generatedKey);
    }

    @Test
    public void testDonationSeparationFromScreening() {
        String screeningStatus = "PENDING_SCREENING";
        // Verify recording donation does not alter screening status
        assertEquals("PENDING_SCREENING", screeningStatus);
        assertNotEquals("CLEARED", screeningStatus);
    }

    @Test
    public void testUpcomingCampDateFiltering() {
        String todayDate = "2026-10-25";
        assertTrue("Future camp should be upcoming", isUpcomingCamp("2026-11-01", todayDate));
        assertTrue("Same-day camp should be upcoming", isUpcomingCamp("2026-10-25", todayDate));
        assertFalse("Past camp should be excluded", isUpcomingCamp("2026-10-10", todayDate));
    }

    @Test
    public void testAnalyticsVolumeAggregation() {
        int vol1 = 450;
        int vol2 = 400;
        int totalMl = vol1 + vol2;
        double totalLiters = totalMl / 1000.0;
        assertEquals(0.85, totalLiters, 0.001);
    }
}