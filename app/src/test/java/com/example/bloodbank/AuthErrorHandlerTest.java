package com.example.bloodbank;

import com.example.bloodbank.data.error.AuthErrorHandler;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthErrorHandlerTest {

    @Test
    public void testNullThrowableHandling() {
        String msg = AuthErrorHandler.getReadableErrorMessage(null);
        assertNotNull(msg);
        assertTrue(msg.contains("unknown error") || msg.contains("try again"));
    }

    @Test
    public void testWeakPasswordException() {
        FirebaseAuthWeakPasswordException ex = new FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Weak password", "too short");
        String msg = AuthErrorHandler.getReadableErrorMessage(ex);
        assertTrue("Expected message about weak password, got: " + msg, msg.toLowerCase().contains("weak"));
    }

    @Test
    public void testUserCollisionException() {
        FirebaseAuthUserCollisionException ex = new FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "Email in use");
        String msg = AuthErrorHandler.getReadableErrorMessage(ex);
        assertTrue("Expected message about email already registered, got: " + msg, msg.toLowerCase().contains("already exists"));
    }

    @Test
    public void testInvalidCredentialsException() {
        FirebaseAuthInvalidCredentialsException ex = new FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "Bad email format");
        String msg = AuthErrorHandler.getReadableErrorMessage(ex);
        assertTrue("Expected message about email formatting, got: " + msg, msg.toLowerCase().contains("email"));
    }

    @Test
    public void testNetworkException() {
        FirebaseNetworkException ex = new FirebaseNetworkException("Network disconnected");
        String msg = AuthErrorHandler.getReadableErrorMessage(ex);
        assertTrue("Expected message about network failure, got: " + msg, msg.toLowerCase().contains("network"));
    }
}
