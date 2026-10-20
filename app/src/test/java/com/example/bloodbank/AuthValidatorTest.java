package com.example.bloodbank;

import com.example.bloodbank.data.validation.AuthValidator;

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthValidatorTest {

    @Test
    public void testFullNameValidation() {
        assertTrue(AuthValidator.validateFullName("John Doe").isValid());
        assertTrue(AuthValidator.validateFullName("Al").isValid());

        assertFalse(AuthValidator.validateFullName(null).isValid());
        assertFalse(AuthValidator.validateFullName("").isValid());
        assertFalse(AuthValidator.validateFullName("   ").isValid());
        assertFalse(AuthValidator.validateFullName("A").isValid());
    }

    @Test
    public void testEmailValidation() {
        assertTrue(AuthValidator.validateEmail("donor@example.com").isValid());
        assertTrue(AuthValidator.validateEmail("donor.name+test@domain.co.in").isValid());

        assertFalse(AuthValidator.validateEmail(null).isValid());
        assertFalse(AuthValidator.validateEmail("").isValid());
        assertFalse(AuthValidator.validateEmail("not-an-email").isValid());
        assertFalse(AuthValidator.validateEmail("donor@").isValid());
        assertFalse(AuthValidator.validateEmail("@example.com").isValid());
        assertFalse(AuthValidator.validateEmail("donor@domain").isValid());
    }

    @Test
    public void testPasswordValidation() {
        assertTrue(AuthValidator.validatePassword("secret123").isValid());
        assertTrue(AuthValidator.validatePassword("123456").isValid());

        assertFalse(AuthValidator.validatePassword(null).isValid());
        assertFalse(AuthValidator.validatePassword("").isValid());
        assertFalse(AuthValidator.validatePassword("12345").isValid());
    }

    @Test
    public void testPasswordConfirmation() {
        assertTrue(AuthValidator.validatePasswordConfirmation("secret123", "secret123").isValid());

        assertFalse(AuthValidator.validatePasswordConfirmation("secret123", "different123").isValid());
        assertFalse(AuthValidator.validatePasswordConfirmation("secret123", null).isValid());
        assertFalse(AuthValidator.validatePasswordConfirmation("12345", "12345").isValid()); // Too short
    }

    @Test
    public void testPhoneValidation() {
        assertTrue(AuthValidator.validatePhone("9876543210").isValid());
        assertTrue(AuthValidator.validatePhone("1234567890").isValid());

        assertFalse(AuthValidator.validatePhone(null).isValid());
        assertFalse(AuthValidator.validatePhone("").isValid());
        assertFalse(AuthValidator.validatePhone("987654321").isValid()); // 9 digits
        assertFalse(AuthValidator.validatePhone("98765432100").isValid()); // 11 digits
        assertFalse(AuthValidator.validatePhone("98765abcde").isValid()); // non-digits
    }

    @Test
    public void testAgeValidation() {
        assertTrue(AuthValidator.validateAge(18).isValid());
        assertTrue(AuthValidator.validateAge(30).isValid());
        assertTrue(AuthValidator.validateAge(65).isValid());

        assertFalse(AuthValidator.validateAge(17).isValid());
        assertFalse(AuthValidator.validateAge(66).isValid());
        assertFalse(AuthValidator.validateAge(0).isValid());
        assertFalse(AuthValidator.validateAge(-5).isValid());

        assertTrue(AuthValidator.validateAgeString("25").isValid());
        assertFalse(AuthValidator.validateAgeString("not_a_number").isValid());
        assertFalse(AuthValidator.validateAgeString(null).isValid());
    }

    @Test
    public void testBloodGroupValidation() {
        String[] validGroups = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};
        for (String bg : validGroups) {
            assertTrue("Expected " + bg + " to be valid", AuthValidator.validateBloodGroup(bg).isValid());
        }

        assertFalse(AuthValidator.validateBloodGroup(null).isValid());
        assertFalse(AuthValidator.validateBloodGroup("").isValid());
        assertFalse(AuthValidator.validateBloodGroup("C+").isValid());
        assertFalse(AuthValidator.validateBloodGroup("O").isValid());
        assertFalse(AuthValidator.validateBloodGroup("UNKNOWN").isValid());
    }

    @Test
    public void testOrganisationValidation() {
        assertTrue(AuthValidator.validateOrganisation("Red Cross").isValid());
        assertTrue(AuthValidator.validateOrganisation("City Hospital Blood Center").isValid());

        assertFalse(AuthValidator.validateOrganisation(null).isValid());
        assertFalse(AuthValidator.validateOrganisation("").isValid());
        assertFalse(AuthValidator.validateOrganisation(" ").isValid());
        assertFalse(AuthValidator.validateOrganisation("A").isValid());
    }
}
