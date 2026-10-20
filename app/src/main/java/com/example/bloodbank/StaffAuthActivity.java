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
import com.example.bloodbank.data.model.StaffProfile;
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
public class StaffAuthActivity extends AppCompatActivity {

    private static final String TAG = "StaffAuthActivity";

    private EditText etStaffName;
    private EditText etStaffEmail;
    private EditText etStaffPassword;
    private EditText etStaffConfirmPassword;
    private EditText etStaffOrg;
    private EditText etStaffMobile;
    private Spinner spStaffGender;
    private LinearLayout layoutStaffRegistrationFields;
    private Button btnStaffLogin;
    private MaterialButton btnStaffGoogleLogin;
    private ProgressBar pbStaffAuth;
    private TextView tvStaffForgotPassword;

    private final String[] genders = {"Male", "Female", "Other"};

    private AuthRepository authRepository;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_auth);

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
                                handleGoogleSignInToken(account.getIdToken());
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

        MaterialToolbar toolbar = findViewById(R.id.toolbarStaffAuth);
        toolbar.setNavigationOnClickListener(v -> finish());

        etStaffName = findViewById(R.id.etStaffName);
        etStaffEmail = findViewById(R.id.etStaffEmail);
        etStaffPassword = findViewById(R.id.etStaffPassword);
        etStaffConfirmPassword = findViewById(R.id.etStaffConfirmPassword);
        etStaffOrg = findViewById(R.id.etStaffOrg);
        etStaffMobile = findViewById(R.id.etStaffMobile);
        spStaffGender = findViewById(R.id.spStaffGender);
        layoutStaffRegistrationFields = findViewById(R.id.layoutStaffRegistrationFields);
        btnStaffLogin = findViewById(R.id.btnStaffLogin);
        btnStaffGoogleLogin = findViewById(R.id.btnStaffGoogleLogin);
        pbStaffAuth = findViewById(R.id.pbStaffAuth);
        tvStaffForgotPassword = findViewById(R.id.tvStaffForgotPassword);
        MaterialButtonToggleGroup toggleGroupStaffMode = findViewById(R.id.toggleGroupStaffMode);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStaffGender.setAdapter(adapter);

        toggleGroupStaffMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnModeStaffRegister) {
                    isRegisterMode = true;
                    layoutStaffRegistrationFields.setVisibility(View.VISIBLE);
                    btnStaffLogin.setText(R.string.mode_register);
                    if (tvStaffForgotPassword != null) {
                        tvStaffForgotPassword.setVisibility(View.GONE);
                    }
                } else {
                    isRegisterMode = false;
                    layoutStaffRegistrationFields.setVisibility(View.GONE);
                    btnStaffLogin.setText(R.string.mode_sign_in);
                    if (tvStaffForgotPassword != null) {
                        tvStaffForgotPassword.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        btnStaffLogin.setOnClickListener(v -> {
            if (isRegisterMode) {
                performStaffRegistration();
            } else {
                performStaffSignIn();
            }
        });

        btnStaffGoogleLogin.setOnClickListener(v -> {
            setLoading(true);
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        tvStaffForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void performStaffSignIn() {
        String email = etStaffEmail.getText().toString().trim();
        String password = etStaffPassword.getText().toString().trim();

        setLoading(true);
        authRepository.signInStaff(email, password, new AuthRepository.StaffSignInCallback() {
            @Override
            public void onSuccess(StaffProfile profile) {
                setLoading(false);
                showSnackbar("Staff authentication successful!");
                navigateToStaffDashboard();
            }

            @Override
            public void onAccessDenied(String reason) {
                setLoading(false);
                new MaterialAlertDialogBuilder(StaffAuthActivity.this)
                        .setTitle("Staff Authorization Required")
                        .setMessage(reason)
                        .setPositiveButton("Understood", null)
                        .show();
            }

            @Override
            public void onMissingProfile(String uid, String email) {
                setLoading(false);
                new MaterialAlertDialogBuilder(StaffAuthActivity.this)
                        .setTitle("Missing Staff Profile")
                        .setMessage("Your account has verified Staff authorization, but no profile record was found at /staffs/" + uid + " in Firestore.\n\nPlease contact your administrator to provision your profile.")
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(String userFriendlyError) {
                setLoading(false);
                showSnackbar(userFriendlyError);
            }
        });
    }

    private void performStaffRegistration() {
        String name = etStaffName.getText().toString().trim();
        String email = etStaffEmail.getText().toString().trim();
        String password = etStaffPassword.getText().toString().trim();
        String confirmPassword = etStaffConfirmPassword.getText().toString().trim();
        String org = etStaffOrg.getText().toString().trim();
        String mobile = etStaffMobile.getText().toString().trim();
        String gender = spStaffGender.getSelectedItem() != null ? spStaffGender.getSelectedItem().toString() : "Other";

        setLoading(true);
        authRepository.registerStaff(name, email, password, confirmPassword, org, mobile, gender,
                new AuthRepository.StaffRegistrationCallback() {
                    @Override
                    public void onPendingApproval(String message) {
                        setLoading(false);
                        new MaterialAlertDialogBuilder(StaffAuthActivity.this)
                                .setTitle("Registration Submitted")
                                .setMessage(message)
                                .setPositiveButton("Go to Sign In", (d, w) -> {
                                    isRegisterMode = false;
                                    layoutStaffRegistrationFields.setVisibility(View.GONE);
                                    btnStaffLogin.setText(R.string.mode_sign_in);
                                })
                                .show();
                    }

                    @Override
                    public void onError(String userFriendlyError) {
                        setLoading(false);
                        showSnackbar(userFriendlyError);
                    }
                });
    }

    private void handleGoogleSignInToken(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        authRepository.getAuth().signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authRepository.getAuth().getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showSnackbar("Google authentication failed.");
                        return;
                    }

                    // Enforce custom claim check for Google Sign-In as well!
                    user.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                boolean isStaff = AuthRepository.verifyStaffCustomClaim(tokenResult);
                                if (!isStaff) {
                                    authRepository.getAuth().signOut();
                                    authRepository.getSessionManager().clearSession();
                                    setLoading(false);
                                    new MaterialAlertDialogBuilder(StaffAuthActivity.this)
                                            .setTitle("Staff Authorization Required")
                                            .setMessage("Access Denied: The Google account (" + user.getEmail() + ") does not have the 'role: Staff' custom claim provisioned.\n\nOnly authorized staff members may access this portal.")
                                            .setPositiveButton("OK", null)
                                            .show();
                                    return;
                                }

                                String uid = user.getUid();
                                authRepository.getDb().collection("staffs").document(uid).get()
                                        .addOnSuccessListener(doc -> {
                                            setLoading(false);
                                            StaffProfile profile = (doc != null && doc.exists())
                                                    ? StaffProfile.fromSnapshot(doc)
                                                    : new StaffProfile(uid, user.getDisplayName(), user.getEmail(), "Blood Bank Staff", "Not provided", "Other", System.currentTimeMillis(), System.currentTimeMillis());

                                            authRepository.getSessionManager().saveStaffSession(profile, true);
                                            navigateToStaffDashboard();
                                        })
                                        .addOnFailureListener(e -> {
                                            setLoading(false);
                                            showSnackbar("Failed to fetch staff profile: " + AuthErrorHandler.getReadableErrorMessage(e));
                                        });
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showSnackbar("Failed to verify authorization token: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar("Google Sign-In Failed: " + AuthErrorHandler.getReadableErrorMessage(e));
                });
    }

    private void setLoading(boolean isLoading) {
        if (pbStaffAuth != null) {
            pbStaffAuth.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        btnStaffLogin.setEnabled(!isLoading);
        btnStaffGoogleLogin.setEnabled(!isLoading);
        if (tvStaffForgotPassword != null) {
            tvStaffForgotPassword.setEnabled(!isLoading);
        }
    }

    private void handleForgotPassword() {
        String email = etStaffEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showPromptEmailDialog("");
        } else {
            AuthValidator.ValidationResult result = AuthValidator.validateEmail(email);
            if (!result.isValid()) {
                etStaffEmail.setError(result.getErrorMessage());
                etStaffEmail.requestFocus();
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
                    etStaffEmail.setText(entered);
                    executePasswordReset(entered);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void executePasswordReset(String email) {
        AuthValidator.ValidationResult result = AuthValidator.validateEmail(email);
        if (!result.isValid()) {
            etStaffEmail.setError(result.getErrorMessage());
            etStaffEmail.requestFocus();
            showSnackbar(result.getErrorMessage());
            return;
        }

        setLoading(true);
        authRepository.sendPasswordResetEmail(email, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String confirmationMessage) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                new MaterialAlertDialogBuilder(StaffAuthActivity.this)
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

    private void navigateToStaffDashboard() {
        Intent intent = new Intent(StaffAuthActivity.this, MainActivity.class);
        startActivity(intent);
        finishAffinity();
    }

    private void showSnackbar(String message) {
        View layout = findViewById(R.id.coordinatorLayoutStaff);
        if (layout != null) {
            Snackbar.make(layout, message, Snackbar.LENGTH_LONG).show();
        }
    }
}