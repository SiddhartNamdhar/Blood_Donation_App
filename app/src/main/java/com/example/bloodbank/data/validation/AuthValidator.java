package com.example.bloodbank.data.validation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates registration and authentication inputs before network requests.
 */
public class AuthValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

    private static final Set<String> VALID_BLOOD_GROUPS = new HashSet<>(Arrays.asList(
            "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"
    ));

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static ValidationResult validateFullName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Full name is required.");
        }
        if (name.trim().length() < 2) {
            return ValidationResult.error("Full name must be at least 2 characters.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.error("Email address is required.");
        }
        String trimmed = email.trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.error("Please enter a valid email address.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.error("Password is required.");
        }
        if (password.length() < 6) {
            return ValidationResult.error("Password must be at least 6 characters.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validatePasswordConfirmation(String password, String confirmPassword) {
        ValidationResult pwResult = validatePassword(password);
        if (!pwResult.isValid()) {
            return pwResult;
        }
        if (!password.equals(confirmPassword)) {
            return ValidationResult.error("Passwords do not match.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return ValidationResult.error("Mobile phone number is required.");
        }
        if (!PHONE_PATTERN.matcher(phone.trim()).matches()) {
            return ValidationResult.error("Mobile phone number must be exactly 10 digits.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateAge(int age) {
        if (age < 18 || age > 65) {
            return ValidationResult.error("Donor age must be between 18 and 65 years.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateAgeString(String ageStr) {
        if (ageStr == null || ageStr.trim().isEmpty()) {
            return ValidationResult.error("Age is required.");
        }
        try {
            int age = Integer.parseInt(ageStr.trim());
            return validateAge(age);
        } catch (NumberFormatException e) {
            return ValidationResult.error("Please enter a valid numerical age.");
        }
    }

    public static ValidationResult validateBloodGroup(String bloodGroup) {
        if (bloodGroup == null || !VALID_BLOOD_GROUPS.contains(bloodGroup.trim())) {
            return ValidationResult.error("Please select a valid blood group.");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateOrganisation(String organisation) {
        if (organisation == null || organisation.trim().isEmpty()) {
            return ValidationResult.error("Organisation name is required.");
        }
        if (organisation.trim().length() < 2) {
            return ValidationResult.error("Organisation name must be at least 2 characters.");
        }
        return ValidationResult.ok();
    }
}
