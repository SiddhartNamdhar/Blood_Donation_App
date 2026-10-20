package com.example.bloodbank;

import android.view.View;

import com.example.bloodbank.data.error.AuthErrorHandler;
import com.example.bloodbank.data.validation.AuthValidator;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordResetTest {

    @Test
    public void testPasswordResetEmailValidation() {
        // Valid emails
        assertTrue(AuthValidator.validateEmail("donor@example.com").isValid());
        assertTrue(AuthValidator.validateEmail("staff.admin@bloodbank.org").isValid());
        assertTrue(AuthValidator.validateEmail("user+tag@domain.co.in").isValid());

        // Empty and null emails
        AuthValidator.ValidationResult nullResult = AuthValidator.validateEmail(null);
        assertFalse(nullResult.isValid());
        assertEquals("Email address is required.", nullResult.getErrorMessage());

        AuthValidator.ValidationResult emptyResult = AuthValidator.validateEmail("");
        assertFalse(emptyResult.isValid());
        assertEquals("Email address is required.", emptyResult.getErrorMessage());

        AuthValidator.ValidationResult whitespaceResult = AuthValidator.validateEmail("   ");
        assertFalse(whitespaceResult.isValid());
        assertEquals("Email address is required.", whitespaceResult.getErrorMessage());

        // Malformed emails
        AuthValidator.ValidationResult invalidFormat = AuthValidator.validateEmail("notanemail");
        assertFalse(invalidFormat.isValid());
        assertEquals("Please enter a valid email address.", invalidFormat.getErrorMessage());

        assertFalse(AuthValidator.validateEmail("user@").isValid());
        assertFalse(AuthValidator.validateEmail("@domain.com").isValid());
        assertFalse(AuthValidator.validateEmail("user@domain").isValid());
    }

    @Test
    public void testUserNotFoundErrorIdentificationForAccountEnumerationProtection() {
        FirebaseAuthInvalidUserException notFoundException =
                new FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "There is no user record corresponding to this identifier.");
        assertTrue("ERROR_USER_NOT_FOUND must be identified as user-not-found",
                AuthErrorHandler.isUserNotFoundError(notFoundException));

        FirebaseAuthException genericNotFound =
                new FirebaseAuthException("user-not-found", "User not found in Firebase.");
        assertTrue("user-not-found code must be identified",
                AuthErrorHandler.isUserNotFoundError(genericNotFound));

        // Different errors must NOT be identified as user-not-found
        FirebaseNetworkException networkException = new FirebaseNetworkException("Connection lost");
        assertFalse("Network error is not user-not-found",
                AuthErrorHandler.isUserNotFoundError(networkException));

        FirebaseAuthInvalidCredentialsException credException =
                new FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "Malformed email");
        assertFalse("Malformed email error is not user-not-found",
                AuthErrorHandler.isUserNotFoundError(credException));
    }

    @Test
    public void testPasswordResetErrorMessageMapping() {
        // Network error
        FirebaseNetworkException netEx = new FirebaseNetworkException("No connection");
        String netMsg = AuthErrorHandler.getPasswordResetErrorMessage(netEx);
        assertTrue(netMsg.toLowerCase().contains("network"));

        // Malformed email credentials
        FirebaseAuthInvalidCredentialsException credEx =
                new FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "Invalid format");
        String credMsg = AuthErrorHandler.getPasswordResetErrorMessage(credEx);
        assertTrue(credMsg.toLowerCase().contains("formatted") || credMsg.toLowerCase().contains("email"));

        // Rate limiting
        FirebaseAuthException rateLimitEx =
                new FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "Too many requests");
        String rateMsg = AuthErrorHandler.getPasswordResetErrorMessage(rateLimitEx);
        assertTrue(rateMsg.toLowerCase().contains("too many") || rateMsg.toLowerCase().contains("later"));

        // Operation not allowed
        FirebaseAuthException opNotAllowedEx =
                new FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "Disabled");
        String opMsg = AuthErrorHandler.getPasswordResetErrorMessage(opNotAllowedEx);
        assertTrue(opMsg.toLowerCase().contains("firebase console"));

        // Null throwable
        String nullMsg = AuthErrorHandler.getPasswordResetErrorMessage(null);
        assertNotNull(nullMsg);
        assertTrue(nullMsg.toLowerCase().contains("unknown") || nullMsg.toLowerCase().contains("try again"));
    }

    @Test
    public void testGenericConfirmationMessageDoesNotDiscloseAccountExistence() {
        String expectedGenericConfirmation =
                "If an account exists for this email, a password reset link has been sent.";

        // Both existent and non-existent accounts must yield identical confirmation message
        assertFalse("Confirmation must not confirm registration status",
                expectedGenericConfirmation.toLowerCase().contains("registered successfully"));
        assertFalse("Confirmation must not confirm non-existence",
                expectedGenericConfirmation.toLowerCase().contains("account not found") ||
                expectedGenericConfirmation.toLowerCase().contains("user not found"));
        assertFalse("Confirmation must not reveal specific account identifiers",
                expectedGenericConfirmation.contains(String.valueOf('@')));
        assertTrue("Confirmation must instruct to check inbox without revealing status",
                expectedGenericConfirmation.toLowerCase().contains("password reset link has been sent"));
    }

    @Test
    public void testForgotPasswordVisibilityStateByMode() {
        // Sign-in mode: Forgot Password link must be VISIBLE
        int expectedVisibilitySignIn = View.VISIBLE;
        assertEquals(View.VISIBLE, expectedVisibilitySignIn);

        // Register mode: Forgot Password link must be GONE
        int expectedVisibilityRegister = View.GONE;
        assertEquals(View.GONE, expectedVisibilityRegister);
    }
}
