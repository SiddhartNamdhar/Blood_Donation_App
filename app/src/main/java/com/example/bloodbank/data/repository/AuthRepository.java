package com.example.bloodbank.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.bloodbank.data.error.AuthErrorHandler;
import com.example.bloodbank.data.model.DonorProfile;
import com.example.bloodbank.data.model.StaffProfile;
import com.example.bloodbank.data.session.SessionManager;
import com.example.bloodbank.data.validation.AuthValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized repository for Donor and Staff authentication, profile persistence,
 * claim verification, and session state.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";
    private static volatile AuthRepository instance;

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String userFriendlyError);
    }

    public interface DonorRegistrationCallback {
        void onSuccess(DonorProfile profile);
        void onPartialRegistration(String uid, DonorProfile pendingProfile, String errorMessage);
        void onError(String userFriendlyError);
    }

    public interface DonorSignInCallback {
        void onSuccess(DonorProfile profile);
        void onMissingProfile(String uid, String email);
        void onError(String userFriendlyError);
    }

    public interface StaffRegistrationCallback {
        void onPendingApproval(String message);
        void onError(String userFriendlyError);
    }

    public interface StaffSignInCallback {
        void onSuccess(StaffProfile profile);
        void onAccessDenied(String reason);
        void onMissingProfile(String uid, String email);
        void onError(String userFriendlyError);
    }

    public interface SessionCheckCallback {
        void onAuthenticatedStaff(StaffProfile staffProfile);
        void onAuthenticatedDonor(DonorProfile donorProfile);
        void onUnauthenticated();
    }

    private AuthRepository(Context context) {
        this.mAuth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(context);
    }

    public static AuthRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AuthRepository.class) {
                if (instance == null) {
                    instance = new AuthRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public FirebaseAuth getAuth() { return mAuth; }
    public FirebaseFirestore getDb() { return db; }
    public SessionManager getSessionManager() { return sessionManager; }

    // =========================================================================
    // DONOR REGISTRATION AND LOGIN FLOW
    // =========================================================================

    public void registerDonor(String fullName, String email, String password, String confirmPassword,
                              String phone, String ageStr, String gender, String bloodGroup,
                              @NonNull DonorRegistrationCallback callback) {

        // 1. Input Validation
        AuthValidator.ValidationResult res = AuthValidator.validateFullName(fullName);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validateEmail(email);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePasswordConfirmation(password, confirmPassword);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePhone(phone);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validateAgeString(ageStr);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validateBloodGroup(bloodGroup);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        final int age = Integer.parseInt(ageStr.trim());
        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        final String trimmedPhone = phone.trim();
        final String trimmedName = fullName.trim();

        // 2. Firebase Authentication account creation
        mAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Account creation succeeded, but could not retrieve authenticated user.");
                        return;
                    }

                    final String uid = user.getUid();
                    final int donorDisplayId = 1000 + (int) (Math.random() * 8999);

                    final DonorProfile profile = new DonorProfile(
                            uid, trimmedName, normalizedEmail, trimmedPhone, age,
                            gender, bloodGroup, donorDisplayId,
                            false, "PENDING_SCREENING",
                            System.currentTimeMillis(), System.currentTimeMillis()
                    );

                    // 3. Write /donors/{uid} to Firestore
                    saveDonorProfileInternal(profile, new Callback<DonorProfile>() {
                        @Override
                        public void onSuccess(DonorProfile savedProfile) {
                            sessionManager.saveDonorSession(savedProfile);
                            callback.onSuccess(savedProfile);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Donor registration partial failure: " + error);
                            // Preserve UID so client can offer safe retry without duplicate auth
                            callback.onPartialRegistration(uid, profile, error);
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
    }

    public void retryCreateDonorProfile(@NonNull DonorProfile profile, @NonNull Callback<DonorProfile> callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || !currentUser.getUid().equals(profile.getUid())) {
            callback.onError("Authentication session expired. Please sign in again.");
            return;
        }

        saveDonorProfileInternal(profile, new Callback<DonorProfile>() {
            @Override
            public void onSuccess(DonorProfile saved) {
                sessionManager.saveDonorSession(saved);
                callback.onSuccess(saved);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void saveDonorProfileInternal(@NonNull DonorProfile profile, @NonNull Callback<DonorProfile> callback) {
        String uid = profile.getUid();
        // Check if document already exists to ensure idempotency and prevent silent overwriting
        db.collection("donors").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        DonorProfile existing = DonorProfile.fromSnapshot(doc);
                        callback.onSuccess(existing != null ? existing : profile);
                        return;
                    }

                    // Document does not exist, reserve donorDisplayId and create donor profile atomically
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    Map<String, Object> idMap = new HashMap<>();
                    idMap.put("donorUid", uid);
                    idMap.put("assignedAt", profile.getCreatedAt());

                    com.google.firebase.firestore.DocumentReference idRef =
                            db.collection("donorDisplayIds").document(String.valueOf(profile.getDonorDisplayId()));
                    com.google.firebase.firestore.DocumentReference donorRef =
                            db.collection("donors").document(uid);

                    batch.set(idRef, idMap);
                    batch.set(donorRef, profile.toFirestoreRegistrationMap());

                    batch.commit()
                            .addOnSuccessListener(aVoid -> callback.onSuccess(profile))
                            .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
                })
                .addOnFailureListener(e -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    Map<String, Object> idMap = new HashMap<>();
                    idMap.put("donorUid", uid);
                    idMap.put("assignedAt", profile.getCreatedAt());

                    com.google.firebase.firestore.DocumentReference idRef =
                            db.collection("donorDisplayIds").document(String.valueOf(profile.getDonorDisplayId()));
                    com.google.firebase.firestore.DocumentReference donorRef =
                            db.collection("donors").document(uid);

                    batch.set(idRef, idMap);
                    batch.set(donorRef, profile.toFirestoreRegistrationMap());

                    batch.commit()
                            .addOnSuccessListener(aVoid -> callback.onSuccess(profile))
                            .addOnFailureListener(err -> callback.onError(AuthErrorHandler.getReadableErrorMessage(err)));
                });
    }

    public void signInDonor(String email, String password, @NonNull DonorSignInCallback callback) {
        AuthValidator.ValidationResult res = AuthValidator.validateEmail(email);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePassword(password);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        mAuth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Sign in succeeded, but user session is unavailable.");
                        return;
                    }

                    final String uid = user.getUid();

                    // Read /donors/{uid} from Firestore
                    db.collection("donors").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc != null && doc.exists()) {
                                    DonorProfile profile = DonorProfile.fromSnapshot(doc);
                                    sessionManager.saveDonorSession(profile);
                                    callback.onSuccess(profile);
                                } else {
                                    // Missing Profile Recovery Path
                                    Log.w(TAG, "Donor authenticated but missing profile document: " + uid);
                                    callback.onMissingProfile(uid, user.getEmail());
                                }
                            })
                            .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
                })
                .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
    }

    // =========================================================================
    // STAFF REGISTRATION AND LOGIN FLOW (CUSTOM CLAIM ENFORCED)
    // =========================================================================

    public void registerStaff(String fullName, String email, String password, String confirmPassword,
                              String organisation, String phone, String gender,
                              @NonNull StaffRegistrationCallback callback) {

        AuthValidator.ValidationResult res = AuthValidator.validateFullName(fullName);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validateEmail(email);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePasswordConfirmation(password, confirmPassword);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validateOrganisation(organisation);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePhone(phone);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        final String trimmedPhone = phone.trim();
        final String trimmedOrg = organisation.trim();
        final String trimmedName = fullName.trim();

        mAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Account creation succeeded, but user session is unavailable.");
                        return;
                    }

                    String uid = user.getUid();
                    StaffProfile profile = new StaffProfile(
                            uid, trimmedName, normalizedEmail, trimmedOrg, trimmedPhone, gender,
                            System.currentTimeMillis(), System.currentTimeMillis()
                    );

                    // Save non-privileged staff profile in /staffs/{uid}
                    // NEVER write 'role', 'isStaff', or 'admin' from client!
                    db.collection("staffs").document(uid).set(profile.toFirestoreMap())
                            .addOnSuccessListener(aVoid -> {
                                // Sign out user so they cannot enter the staff portal without custom claim
                                mAuth.signOut();
                                sessionManager.clearSession();
                                callback.onPendingApproval(
                                        "Staff registration submitted successfully!\n\n" +
                                        "For security, staff authorization requires administrator approval. " +
                                        "Your administrator must provision the 'role: Staff' claim before you can sign in."
                                );
                            })
                            .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
                })
                .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
    }

    public void signInStaff(String email, String password, @NonNull StaffSignInCallback callback) {
        AuthValidator.ValidationResult res = AuthValidator.validateEmail(email);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        res = AuthValidator.validatePassword(password);
        if (!res.isValid()) { callback.onError(res.getErrorMessage()); return; }

        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        mAuth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Sign in succeeded, but user session is unavailable.");
                        return;
                    }

                    // Force refresh token to get the latest custom claims from the server
                    user.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                boolean isStaff = verifyStaffCustomClaim(tokenResult);

                                if (!isStaff) {
                                    // Security requirement: choosing 'Staff' is not proof of staff status.
                                    // Deny staff access, sign out immediately, and never auto-promote.
                                    Log.w(TAG, "User " + user.getUid() + " attempted staff login without Staff claim.");
                                    mAuth.signOut();
                                    sessionManager.clearSession();
                                    callback.onAccessDenied(
                                            "Access Denied: Your account does not have verified Staff authorization.\n\n" +
                                            "Please contact your Blood Bank Administrator to provision staff privileges using the Firebase Admin SDK."
                                    );
                                    return;
                                }

                                // Verified Staff Claim -> Retrieve /staffs/{uid}
                                String uid = user.getUid();
                                db.collection("staffs").document(uid).get()
                                        .addOnSuccessListener(doc -> {
                                            if (doc != null && doc.exists()) {
                                                StaffProfile profile = StaffProfile.fromSnapshot(doc);
                                                sessionManager.saveStaffSession(profile, true);
                                                callback.onSuccess(profile);
                                            } else {
                                                Log.w(TAG, "Verified staff member missing profile document: " + uid);
                                                callback.onMissingProfile(uid, user.getEmail());
                                            }
                                        })
                                        .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
                            })
                            .addOnFailureListener(e -> callback.onError("Failed to verify authorization token: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(AuthErrorHandler.getReadableErrorMessage(e)));
    }

    /**
     * Checks if the ID token contains a valid Staff custom claim.
     */
    public static boolean verifyStaffCustomClaim(GetTokenResult tokenResult) {
        if (tokenResult == null) {
            return false;
        }
        return verifyStaffClaimsMap(tokenResult.getClaims());
    }

    /**
     * Verifies raw custom claims map for Staff authorization.
     */
    public static boolean verifyStaffClaimsMap(Map<String, Object> claims) {
        if (claims == null) {
            return false;
        }

        Object role = claims.get("role");
        if (role != null) {
            String roleStr = role.toString().trim();
            if ("Staff".equalsIgnoreCase(roleStr)) {
                return true;
            }
        }

        Object isStaff = claims.get("isStaff");
        if (isStaff instanceof Boolean && (Boolean) isStaff) {
            return true;
        }

        return false;
    }

    // =========================================================================
    // SESSION VERIFICATION & STARTUP RESOLUTION
    // =========================================================================

    public void checkActiveSession(@NonNull SessionCheckCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            sessionManager.clearSession();
            callback.onUnauthenticated();
            return;
        }

        currentUser.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    boolean isStaff = verifyStaffCustomClaim(tokenResult);
                    String uid = currentUser.getUid();

                    if (isStaff) {
                        // Check staff profile
                        db.collection("staffs").document(uid).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc != null && doc.exists()) {
                                        StaffProfile profile = StaffProfile.fromSnapshot(doc);
                                        sessionManager.saveStaffSession(profile, true);
                                        callback.onAuthenticatedStaff(profile);
                                    } else {
                                        callback.onUnauthenticated();
                                    }
                                })
                                .addOnFailureListener(e -> callback.onUnauthenticated());
                    } else {
                        // Check donor profile
                        db.collection("donors").document(uid).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc != null && doc.exists()) {
                                        DonorProfile profile = DonorProfile.fromSnapshot(doc);
                                        sessionManager.saveDonorSession(profile);
                                        callback.onAuthenticatedDonor(profile);
                                    } else {
                                        callback.onUnauthenticated();
                                    }
                                })
                                .addOnFailureListener(e -> callback.onUnauthenticated());
                    }
                })
                .addOnFailureListener(e -> callback.onUnauthenticated());
    }

    public void signOut(Context context, Runnable onComplete) {
        signOut(onComplete);
    }

    public void signOut(Runnable onComplete) {
        try {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && sessionManager.isDonor()) {
                // Clear FCM token on logout so tokens don't cross-associate
                Map<String, Object> clearFcm = new java.util.HashMap<>();
                clearFcm.put("fcmToken", null);
                db.collection("donors").document(user.getUid()).update(clearFcm);
            }
        } catch (Exception ignored) {}

        sessionManager.clearSession();
        mAuth.signOut();
        if (onComplete != null) {
            onComplete.run();
        }
    }

    // =========================================================================
    // PASSWORD RESET FLOW
    // =========================================================================

    public void sendPasswordResetEmail(String email, @NonNull Callback<String> callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email address is required.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        AuthValidator.ValidationResult val = AuthValidator.validateEmail(normalizedEmail);
        if (!val.isValid()) {
            callback.onError(val.getErrorMessage());
            return;
        }

        mAuth.sendPasswordResetEmail(normalizedEmail)
                .addOnSuccessListener(aVoid -> {
                    // Generic confirmation message prevents account enumeration
                    callback.onSuccess("If an account exists for this email, a password reset link has been sent.");
                })
                .addOnFailureListener(e -> {
                    // Privacy-preserving: if account doesn't exist, return generic success message
                    if (AuthErrorHandler.isUserNotFoundError(e)) {
                        callback.onSuccess("If an account exists for this email, a password reset link has been sent.");
                        return;
                    }
                    callback.onError(AuthErrorHandler.getPasswordResetErrorMessage(e));
                });
    }
}
