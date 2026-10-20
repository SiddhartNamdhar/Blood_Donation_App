package com.example.bloodbank;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bloodbank.data.error.AuthErrorHandler;
import com.example.bloodbank.data.model.DonorProfile;
import com.example.bloodbank.data.repository.AuthRepository;
import com.example.bloodbank.data.validation.AuthValidator;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

@SuppressWarnings("deprecation")
public class DonorAuthActivity extends AppCompatActivity {

    private static final String TAG = "DonorAuthActivity";

    private EditText etDonorAuthName;
    private EditText etDonorAuthEmail;
    private EditText etDonorAuthPassword;
    private EditText etDonorAuthConfirmPassword;
    private EditText etDonorAuthMobile;
    private EditText etDonorAuthAge;
    private Spinner spDonorAuthGender;
    private Spinner spDonorAuthBloodGroup;
    private LinearLayout layoutDonorRegistrationFields;
    private Button btnDonorLogin;
    private MaterialButton btnDonorRetryProfile;
    private MaterialButton btnDonorGoogleLogin;
    private ProgressBar pbDonorAuth;
    private TextView tvDonorForgotPassword;

    private final String[] genders = {"Male", "Female", "Other"};
    private final String[] bloodGroups = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};

    private AuthRepository authRepository;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private boolean isRegisterMode = false;
    private DonorProfile pendingProfileForRetry = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor_auth);

        authRepository = AuthRepository.getInstance(this);

        // Configure Google Sign-In
        String defaultWebClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(defaultWebClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null && account.getIdToken() != null) {
                                handleGoogleSignInToken(account.getIdToken(), account.getDisplayName(), account.getEmail());
                            }
                        } catch (ApiException e) {
                            Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode());
                            showSnackbar("Google Sign-In failed (" + e.getStatusCode() + "). Verify SHA-1 in Firebase Console.");
                            setLoading(false);
                        }
                    } else {
                        setLoading(false);
                    }
                }
        );

        MaterialToolbar toolbar = findViewById(R.id.toolbarDonorAuth);
        toolbar.setNavigationOnClickListener(v -> finish());

        etDonorAuthName = findViewById(R.id.etDonorAuthName);
        etDonorAuthEmail = findViewById(R.id.etDonorAuthEmail);
        etDonorAuthPassword = findViewById(R.id.etDonorAuthPassword);
        etDonorAuthConfirmPassword = findViewById(R.id.etDonorAuthConfirmPassword);
        etDonorAuthMobile = findViewById(R.id.etDonorAuthMobile);
        etDonorAuthAge = findViewById(R.id.etDonorAuthAge);
        spDonorAuthGender = findViewById(R.id.spDonorAuthGender);
        spDonorAuthBloodGroup = findViewById(R.id.spDonorAuthBloodGroup);
        layoutDonorRegistrationFields = findViewById(R.id.layoutDonorRegistrationFields);

        btnDonorLogin = findViewById(R.id.btnDonorLogin);
        btnDonorRetryProfile = findViewById(R.id.btnDonorRetryProfile);
        btnDonorGoogleLogin = findViewById(R.id.btnDonorGoogleLogin);
        pbDonorAuth = findViewById(R.id.pbDonorAuth);
        tvDonorForgotPassword = findViewById(R.id.tvDonorForgotPassword);
        MaterialButtonToggleGroup toggleGroupDonorMode = findViewById(R.id.toggleGroupDonorMode);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDonorAuthGender.setAdapter(genderAdapter);

        ArrayAdapter<String> bloodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        bloodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDonorAuthBloodGroup.setAdapter(bloodAdapter);

        // Switch between Sign-In and Register modes
        toggleGroupDonorMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnModeDonorRegister) {
                    switchToRegisterMode();
                } else {
                    switchToSignInMode();
                }
            }
        });

        btnDonorLogin.setOnClickListener(v -> {
            if (isRegisterMode) {
                performRegistration();
            } else {
                performSignIn();
            }
        });

        btnDonorRetryProfile.setOnClickListener(v -> {
            if (pendingProfileForRetry != null) {
                setLoading(true);
                authRepository.retryCreateDonorProfile(pendingProfileForRetry, new AuthRepository.Callback<DonorProfile>() {
                    @Override
                    public void onSuccess(DonorProfile result) {
                        setLoading(false);
                        pendingProfileForRetry = null;
                        btnDonorRetryProfile.setVisibility(View.GONE);
                        showSnackbar("Profile created successfully!");
                        navigateToDonorDashboard();
                    }

                    @Override
                    public void onError(String error) {
                        setLoading(false);
                        showSnackbar("Profile Retry Failed: " + error);
                    }
                });
            }
        });

        btnDonorGoogleLogin.setOnClickListener(v -> {
            setLoading(true);
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        tvDonorForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void switchToRegisterMode() {
        isRegisterMode = true;
        layoutDonorRegistrationFields.setVisibility(View.VISIBLE);
        btnDonorLogin.setText(R.string.mode_register);
        btnDonorRetryProfile.setVisibility(View.GONE);
        if (tvDonorForgotPassword != null) {
            tvDonorForgotPassword.setVisibility(View.GONE);
        }
    }

    private void switchToSignInMode() {
        isRegisterMode = false;
        layoutDonorRegistrationFields.setVisibility(View.GONE);
        btnDonorLogin.setText(R.string.mode_sign_in);
        btnDonorRetryProfile.setVisibility(View.GONE);
        if (tvDonorForgotPassword != null) {
            tvDonorForgotPassword.setVisibility(View.VISIBLE);
        }
    }

    private void performSignIn() {
        String email = etDonorAuthEmail.getText().toString().trim();
        String password = etDonorAuthPassword.getText().toString().trim();

        setLoading(true);
        authRepository.signInDonor(email, password, new AuthRepository.DonorSignInCallback() {
            @Override
            public void onSuccess(DonorProfile profile) {
                setLoading(false);
                showSnackbar("Sign in successful!");
                navigateToDonorDashboard();
            }

            @Override
            public void onMissingProfile(String uid, String userEmail) {
                setLoading(false);
                new MaterialAlertDialogBuilder(DonorAuthActivity.this)
                        .setTitle("Incomplete Donor Profile")
                        .setMessage("Your credentials are valid, but no donor profile document was found in Cloud Firestore.\n\n" +
                                "Would you like to complete registration now?")
                        .setPositiveButton("Complete Profile", (d, w) -> {
                            switchToRegisterMode();
                            if (userEmail != null && !userEmail.isEmpty()) {
                                etDonorAuthEmail.setText(userEmail);
                            }
                            showSnackbar("Please fill in your profile details and tap Register.");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                showSnackbar(error);
            }
        });
    }

    private void performRegistration() {
        String fullName = etDonorAuthName.getText().toString().trim();
        String email = etDonorAuthEmail.getText().toString().trim();
        String password = etDonorAuthPassword.getText().toString().trim();
        String confirmPassword = etDonorAuthConfirmPassword.getText().toString().trim();
        String phone = etDonorAuthMobile.getText().toString().trim();
        String ageStr = etDonorAuthAge.getText().toString().trim();
        String gender = spDonorAuthGender.getSelectedItem() != null ? spDonorAuthGender.getSelectedItem().toString() : "Other";
        String bloodGroup = spDonorAuthBloodGroup.getSelectedItem() != null ? spDonorAuthBloodGroup.getSelectedItem().toString() : "O+";

        setLoading(true);
        authRepository.registerDonor(fullName, email, password, confirmPassword, phone, ageStr, gender, bloodGroup,
                new AuthRepository.DonorRegistrationCallback() {
                    @Override
                    public void onSuccess(DonorProfile profile) {
                        setLoading(false);
                        pendingProfileForRetry = null;
                        btnDonorRetryProfile.setVisibility(View.GONE);
                        showSnackbar("Donor account registered successfully!");
                        navigateToDonorDashboard();
                    }

                    @Override
                    public void onPartialRegistration(String uid, DonorProfile pendingProfile, String errorMessage) {
                        setLoading(false);
                        pendingProfileForRetry = pendingProfile;
                        btnDonorRetryProfile.setVisibility(View.VISIBLE);

                        new MaterialAlertDialogBuilder(DonorAuthActivity.this)
                                .setTitle("Profile Creation Failed")
                                .setMessage("Your login account was created in Firebase Authentication, but saving your donor profile to Firestore failed:\n\n" +
                                        errorMessage + "\n\n" +
                                        "Your login is preserved. Tap 'Retry Profile Creation' to complete registration without creating a duplicate account.")
                                .setPositiveButton("Retry Now", (d, w) -> btnDonorRetryProfile.performClick())
                                .setNegativeButton("Close", null)
                                .show();
                    }

                    @Override
                    public void onError(String userFriendlyError) {
                        setLoading(false);
                        showSnackbar(userFriendlyError);
                    }
                });
    }

    private void handleGoogleSignInToken(String idToken, String displayName, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        authRepository.getAuth().signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authRepository.getAuth().getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showSnackbar("Google authentication failed.");
                        return;
                    }

                    String uid = user.getUid();
                    authRepository.getDb().collection("donors").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                setLoading(false);
                                if (doc != null && doc.exists()) {
                                    DonorProfile profile = DonorProfile.fromSnapshot(doc);
                                    if (profile != null) {
                                        authRepository.getSessionManager().saveDonorSession(profile);
                                        navigateToDonorDashboard();
                                        return;
                                    }
                                }

                                // Profile missing: prompt to complete registration
                                switchToRegisterMode();
                                if (displayName != null) etDonorAuthName.setText(displayName);
                                if (email != null) etDonorAuthEmail.setText(email);
                                showSnackbar("Please complete remaining registration fields (mobile, age, blood group).");
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showSnackbar("Failed to retrieve profile: " + AuthErrorHandler.getReadableErrorMessage(e));
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar("Google Authentication Failed: " + AuthErrorHandler.getReadableErrorMessage(e));
                });
    }

    private void setLoading(boolean isLoading) {
        if (pbDonorAuth != null) {
            pbDonorAuth.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        btnDonorLogin.setEnabled(!isLoading);
        btnDonorGoogleLogin.setEnabled(!isLoading);
        if (tvDonorForgotPassword != null) {
            tvDonorForgotPassword.setEnabled(!isLoading);
        }
        if (btnDonorRetryProfile != null && btnDonorRetryProfile.getVisibility() == View.VISIBLE) {
            btnDonorRetryProfile.setEnabled(!isLoading);
        }
    }

    private void handleForgotPassword() {
        String email = etDonorAuthEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showPromptEmailDialog("");
        } else {
            AuthValidator.ValidationResult result = AuthValidator.validateEmail(email);
            if (!result.isValid()) {
                etDonorAuthEmail.setError(result.getErrorMessage());
                etDonorAuthEmail.requestFocus();
                showPromptEmailDialog(email);
                return;
            }
            executePasswordReset(email);
        }
    }

    private void showPromptEmailDialog(String prefilledEmail) {
        EditText etEmailInput = new EditText(this);
        etEmailInput.setHint(R.string.hint_email);
        etEmailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | android.text.InputType.TYPE_CLASS_TEXT);
        if (prefilledEmail != null && !prefilledEmail.isEmpty()) {
            etEmailInput.setText(prefilledEmail);
            etEmailInput.setSelection(prefilledEmail.length());
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(paddingPx, (int) (8 * getResources().getDisplayMetrics().density), paddingPx, 0);
        container.addView(etEmailInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_reset_password)
                .setMessage(R.string.desc_reset_password_prompt)
                .setView(container)
                .setPositiveButton(R.string.btn_send_reset_link, (dialog, which) -> {
                    String entered = etEmailInput.getText().toString().trim();
                    etDonorAuthEmail.setText(entered);
                    executePasswordReset(entered);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void executePasswordReset(String email) {
        AuthValidator.ValidationResult result = AuthValidator.validateEmail(email);
        if (!result.isValid()) {
            etDonorAuthEmail.setError(result.getErrorMessage());
            etDonorAuthEmail.requestFocus();
            showSnackbar(result.getErrorMessage());
            return;
        }

        setLoading(true);
        authRepository.sendPasswordResetEmail(email, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String confirmationMessage) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                new MaterialAlertDialogBuilder(DonorAuthActivity.this)
                        .setTitle(R.string.title_reset_email_sent)
                        .setMessage(R.string.msg_reset_email_sent)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showSnackbar(error);
            }
        });
    }

    private void navigateToDonorDashboard() {
        Intent intent = new Intent(DonorAuthActivity.this, DonorActivity.class);
        startActivity(intent);
        finishAffinity();
    }

    private void showSnackbar(String message) {
        View layout = findViewById(R.id.coordinatorLayoutDonorAuth);
        if (layout != null) {
            Snackbar.make(layout, message, Snackbar.LENGTH_LONG).show();
        }
    }
}