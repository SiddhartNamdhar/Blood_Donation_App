package com.example.bloodbank.data.error;

import android.util.Log;

import java.util.Locale;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Standardized error parser and user message generator for Firebase Auth and Firestore operations.
 */
public class AuthErrorHandler {

    private static final String TAG = "AuthErrorHandler";

    private static void logWarning(String message) {
        try {
            Log.w(TAG, message);
        } catch (Throwable ignored) {
            // JVM unit test environment where android.util.Log is not mocked
        }
    }

    public static String getReadableErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "An unknown error occurred. Please try again.";
        }

        // Diagnostic log without leaking credentials or tokens
        logWarning("Auth error encountered: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());

        if (throwable instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Please use at least 6 characters.";
        }

        if (throwable instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Please sign in instead.";
        }

        if (throwable instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) throwable).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equalsIgnoreCase(code) || "invalid-email".equalsIgnoreCase(code)) {
                return "The email address is improperly formatted.";
            }
            return "Invalid email or password. Please verify your credentials and try again.";
        }

        if (throwable instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) throwable).getErrorCode();
            if ("ERROR_USER_DISABLED".equalsIgnoreCase(code) || "user-disabled".equalsIgnoreCase(code)) {
                return "This account has been disabled. Please contact support.";
            }
            return "No account found with this email. Please check the address or register.";
        }

        if (throwable instanceof FirebaseNetworkException) {
            return "Network connection failure. Please check your internet connection.";
        }

        if (throwable instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException fe = (FirebaseFirestoreException) throwable;
            if (fe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Access denied by security rules. Insufficient permissions for this operation.";
            }
            if (fe.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "Database service is temporarily unavailable. Please try again in a few moments.";
            }
            return "Database operation failed: " + fe.getMessage();
        }

        if (throwable instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) throwable).getErrorCode();
            if (code != null) {
                switch (code.toLowerCase(Locale.ROOT)) {
                    case "error_email_already_in_use":
                    case "email-already-in-use":
                        return "An account with this email already exists. Please sign in instead.";
                    case "error_wrong_password":
                    case "wrong-password":
                    case "invalid-credential":
                        return "Incorrect email or password. Please try again.";
                    case "error_user_not_found":
                    case "user-not-found":
                        return "No registered account found with this email.";
                    case "error_user_disabled":
                    case "user-disabled":
                        return "This account has been disabled. Contact blood bank support.";
                    case "error_too_many_requests":
                    case "too-many-requests":
                        return "Too many unsuccessful attempts. Please try again later.";
                    case "error_operation_not_allowed":
                    case "operation-not-allowed":
                        return "Email/Password sign-in is not enabled in Firebase Console.";
                    default:
                        break;
                }
            }
        }

        String msg = throwable.getMessage();
        if (msg != null && !msg.trim().isEmpty()) {
            return msg;
        }

        return "An unexpected error occurred. Please try again.";
    }

    /**
     * Identifies whether the error indicates the user/email was not found.
     * Used to prevent account enumeration by returning a generic privacy-preserving confirmation.
     */
    public static boolean isUserNotFoundError(Throwable throwable) {
        if (throwable instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) throwable).getErrorCode();
            return "ERROR_USER_NOT_FOUND".equalsIgnoreCase(code) || "user-not-found".equalsIgnoreCase(code);
        }
        if (throwable instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) throwable).getErrorCode();
            return "ERROR_USER_NOT_FOUND".equalsIgnoreCase(code) || "user-not-found".equalsIgnoreCase(code);
        }
        return false;
    }

    /**
     * Standardized user-friendly error message for password reset requests.
     */
    public static String getPasswordResetErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "An unknown error occurred. Please try again.";
        }

        logWarning("Password reset error: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());

        if (throwable instanceof FirebaseNetworkException) {
            return "Network connection failure. Please check your internet connection.";
        }

        if (throwable instanceof FirebaseAuthInvalidCredentialsException) {
            return "The email address is improperly formatted.";
        }

        if (throwable instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) throwable).getErrorCode();
            if (code != null) {
                switch (code.toLowerCase(Locale.ROOT)) {
                    case "error_invalid_email":
                    case "invalid-email":
                        return "The email address is improperly formatted.";
                    case "error_too_many_requests":
                    case "too-many-requests":
                        return "Too many requests. Please wait a few moments before trying again.";
                    case "error_operation_not_allowed":
                    case "operation-not-allowed":
                        return "Password reset via email is not enabled in Firebase Console.";
                    default:
                        break;
                }
            }
        }

        return "Unable to send password reset email at this time. Please try again later.";
    }
}
