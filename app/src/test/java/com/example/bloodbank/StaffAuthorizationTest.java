package com.example.bloodbank;

import com.example.bloodbank.data.repository.AuthRepository;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class StaffAuthorizationTest {

    @Test
    public void testValidStaffRoleClaimAccepted() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "Staff");
        assertTrue("Staff role should be verified", AuthRepository.verifyStaffClaimsMap(claims));
    }

    @Test
    public void testCaseInsensitiveStaffRoleAccepted() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "staff");
        assertTrue("staff (lowercase) role should be accepted", AuthRepository.verifyStaffClaimsMap(claims));
    }

    @Test
    public void testIsStaffBooleanClaimAccepted() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isStaff", true);
        assertTrue("isStaff: true boolean claim should be accepted", AuthRepository.verifyStaffClaimsMap(claims));
    }

    @Test
    public void testMissingOrInvalidClaimsDenied() {
        assertFalse("Null claims map should be denied", AuthRepository.verifyStaffClaimsMap(null));
        assertFalse("Empty claims map should be denied", AuthRepository.verifyStaffClaimsMap(Collections.emptyMap()));

        Map<String, Object> donorClaims = new HashMap<>();
        donorClaims.put("role", "Donor");
        assertFalse("Donor role should be denied staff access", AuthRepository.verifyStaffClaimsMap(donorClaims));

        Map<String, Object> userClaims = new HashMap<>();
        userClaims.put("role", "User");
        assertFalse("User role should be denied staff access", AuthRepository.verifyStaffClaimsMap(userClaims));

        Map<String, Object> falseStaffClaims = new HashMap<>();
        falseStaffClaims.put("isStaff", false);
        assertFalse("isStaff: false should be denied staff access", AuthRepository.verifyStaffClaimsMap(falseStaffClaims));
    }
}
