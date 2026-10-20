package com.example.bloodbank;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView imgCampBanner;
    private EditText etDonorId, etDonorName, etDonorAge, etBloodGroupPlaceholder, etBloodDonated, etHemoglobin;
    private ListView lvDonors;

    private LinearLayout layoutDonation;
    private RelativeLayout layoutRequest;
    private RelativeLayout layoutOrganise;
    private LinearLayout containerOngoingRequests;
    private LinearLayout containerCompletedRequests;
    private LinearLayout containerCamps;

    private DBHelper dbHelper;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration campsListener;

    private final ArrayList<Integer> idList = new ArrayList<>();
    private final ArrayList<String> nameList = new ArrayList<>();
    private final ArrayList<Integer> ageList = new ArrayList<>();
    private final ArrayList<String> bloodGroupList = new ArrayList<>();

    private final String[] bloodGroups = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};
    private String selectedFilterBloodGroup = "All";
    private String currentStaffUid = "";
    private String selectedCampId = null;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DBHelper(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        currentStaffUid = currentUser.getUid();

        // Enforce staff authorization custom claim on dashboard entry
        currentUser.getIdToken(false).addOnSuccessListener(tokenResult -> {
            if (!com.example.bloodbank.data.repository.AuthRepository.verifyStaffCustomClaim(tokenResult)) {
                Log.w(TAG, "Unauthorized staff dashboard entry denied for UID: " + currentStaffUid);
                com.example.bloodbank.data.repository.AuthRepository.getInstance(this).signOut(this, () -> {
                    Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
                    startActivity(intent);
                    finishAffinity();
                });
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Token verification failed on dashboard entry: " + e.getMessage());
            com.example.bloodbank.data.repository.AuthRepository.getInstance(this).signOut(this, () -> {
                Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
                startActivity(intent);
                finishAffinity();
            });
        });

        // Setup MaterialToolbar & ActionBar
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        if (topAppBar.getOverflowIcon() != null) {
            topAppBar.getOverflowIcon().setTint(Color.WHITE);
        }

        // Register ActivityResultLaunchers for Gallery and Camera
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && imgCampBanner != null) {
                        imgCampBanner.setImageURI(uri);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null && imgCampBanner != null) {
                        imgCampBanner.setImageBitmap(bitmap);
                    }
                }
        );



        // Bind Main Layout Views
        imgCampBanner = findViewById(R.id.imgCampBanner);
        etDonorId = findViewById(R.id.etDonorId);
        etDonorName = findViewById(R.id.etDonorName);
        etDonorAge = findViewById(R.id.etDonorAge);
        etBloodGroupPlaceholder = findViewById(R.id.etBloodGroupPlaceholder);
        etBloodDonated = findViewById(R.id.etBloodDonated);
        etHemoglobin = findViewById(R.id.etHemoglobin);
        Button btnAdd = findViewById(R.id.btnAdd);
        lvDonors = findViewById(R.id.lvDonors);
        ChipGroup chipGroupFilter = findViewById(R.id.chipGroupFilter);

        // Bottom Navigation Layout Sections
        layoutDonation = findViewById(R.id.layoutDonation);
        layoutRequest = findViewById(R.id.layoutRequest);
        layoutOrganise = findViewById(R.id.layoutOrganise);
        containerOngoingRequests = findViewById(R.id.containerOngoingRequests);
        containerCompletedRequests = findViewById(R.id.containerCompletedRequests);
        containerCamps = findViewById(R.id.containerCamps);

        FloatingActionButton fabAddRequest = findViewById(R.id.fabAddRequest);
        FloatingActionButton fabOrganiseCamp = findViewById(R.id.fabOrganiseCamp);
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // Initial Request Complete Buttons
        MaterialCardView cardRequest1 = findViewById(R.id.cardRequest1);
        MaterialCardView cardRequest2 = findViewById(R.id.cardRequest2);
        MaterialButton btnCompleteReq1 = findViewById(R.id.btnCompleteReq1);
        MaterialButton btnCompleteReq2 = findViewById(R.id.btnCompleteReq2);

        if (btnCompleteReq1 != null && cardRequest1 != null) {
            btnCompleteReq1.setOnClickListener(v -> markRequestCompleted(cardRequest1, btnCompleteReq1, "🚨 Urgent O- Blood Required (COMPLETED)"));
        }

        if (btnCompleteReq2 != null && cardRequest2 != null) {
            btnCompleteReq2.setOnClickListener(v -> markRequestCompleted(cardRequest2, btnCompleteReq2, "🚨 A+ Surgery Requirement (COMPLETED)"));
        }

        // Camp Banner Popup Menu
        imgCampBanner.setOnClickListener(this::showBannerPopupMenu);

        // Auto-fill Donor Details from Cloud Firestore as Authoritative Source
        etDonorId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String idStr = s.toString().trim();
                if (!idStr.isEmpty()) {
                    try {
                        int donorId = Integer.parseInt(idStr);

                        // Query Firestore as Primary Authoritative Source
                        db.collection("donors").whereEqualTo("donorDisplayId", donorId).get()
                                .addOnSuccessListener(snapshots -> {
                                    if (snapshots != null && !snapshots.isEmpty()) {
                                        DocumentSnapshot doc = snapshots.getDocuments().get(0);
                                        String name = doc.getString("name");
                                        Long ageLong = doc.getLong("age");
                                        String bg = doc.getString("bloodGroup");

                                        if (name != null) etDonorName.setText(name);
                                        if (ageLong != null) etDonorAge.setText(String.valueOf(ageLong));
                                        if (bg != null) etBloodGroupPlaceholder.setText(bg);
                                    } else {
                                        // Fallback to local SQLite cache
                                        Cursor cursor = dbHelper.getDonorById(donorId);
                                        if (cursor != null && cursor.moveToFirst()) {
                                            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                                            int age = cursor.getInt(cursor.getColumnIndexOrThrow("age"));
                                            String bg = cursor.getString(cursor.getColumnIndexOrThrow("bloodGroup"));

                                            etDonorName.setText(name);
                                            etDonorAge.setText(String.valueOf(age));
                                            etBloodGroupPlaceholder.setText(bg);
                                            cursor.close();
                                        } else {
                                            etDonorName.setText("");
                                            etDonorAge.setText("");
                                            etBloodGroupPlaceholder.setText("");
                                        }
                                    }
                                });
                    } catch (NumberFormatException ignored) {
                        etDonorName.setText("");
                        etDonorAge.setText("");
                        etBloodGroupPlaceholder.setText("");
                    }
                } else {
                    etDonorName.setText("");
                    etDonorAge.setText("");
                    etBloodGroupPlaceholder.setText("");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup ChipGroup Blood Group Filter
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedFilterBloodGroup = "All";
            } else {
                int checkedId = checkedIds.get(0);
                Chip chip = findViewById(checkedId);
                if (chip != null) {
                    selectedFilterBloodGroup = chip.getText().toString();
                } else {
                    selectedFilterBloodGroup = "All";
                }
            }
            refreshList();
        });

        // Load Donors List
        refreshList();

        // Tapping a donor fills the form
        lvDonors.setOnItemClickListener((parent, view, position, id) -> {
            if (position < idList.size()) {
                int selectedDonorId = idList.get(position);
                etDonorId.setText(String.valueOf(selectedDonorId));
            }
        });

        // Register ListView for Context Menu
        registerForContextMenu(lvDonors);

        // Record Donation Button with Atomic Transaction & Strict Validation
        btnAdd.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            String bloodDonatedInput = etBloodDonated.getText().toString().trim();
            String hemoglobinInput = etHemoglobin.getText().toString().trim();

            if (idStr.isEmpty()) {
                showSnackbar("Please enter a Donor ID");
                return;
            }

            int donorId;
            try {
                donorId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                showSnackbar("Please enter a valid numeric Donor ID");
                return;
            }

            String name = etDonorName.getText().toString().trim();
            String bloodGroup = etBloodGroupPlaceholder.getText().toString().trim();

            if (name.isEmpty() || bloodGroup.isEmpty()) {
                showSnackbar("Please enter a valid Donor ID to auto-fill details");
                return;
            }

            // Parse and Validate Blood Volume (200 - 500 ml)
            int bloodVolume;
            try {
                String volumeDigits = bloodDonatedInput.replaceAll("[^0-9]", "");
                if (volumeDigits.isEmpty()) {
                    showSnackbar("Please enter a valid blood volume (e.g., 450 ml)");
                    return;
                }
                bloodVolume = Integer.parseInt(volumeDigits);
                if (bloodVolume < 200 || bloodVolume > 500) {
                    showSnackbar("Please enter a valid blood volume between 200 and 500 ml");
                    return;
                }
            } catch (NumberFormatException e) {
                showSnackbar("Please enter a valid numeric blood volume (e.g., 450 ml)");
                return;
            }

            // Parse and Validate Hemoglobin (7.0 - 20.0 g/dL)
            double hb;
            try {
                String hbClean = hemoglobinInput.replaceAll("[^0-9.]", "");
                if (hbClean.isEmpty()) {
                    showSnackbar("Please enter a valid hemoglobin value (e.g., 13.8)");
                    return;
                }
                hb = Double.parseDouble(hbClean);
                if (Double.isNaN(hb) || Double.isInfinite(hb) || hb < 7.0 || hb > 20.0) {
                    showSnackbar("Please enter a valid hemoglobin value between 7.0 and 20.0 g/dL");
                    return;
                }
            } catch (NumberFormatException e) {
                showSnackbar("Please enter a valid numeric hemoglobin value (e.g., 13.8)");
                return;
            }

            if (currentStaffUid == null || currentStaffUid.trim().isEmpty()) {
                showSnackbar("Access Denied: Staff session missing or unauthenticated.");
                return;
            }

            final int finalVolume = bloodVolume;
            final double finalHb = hb;

            // Resolve real donor UID from Firestore Cloud Authoritative Database
            db.collection("donors").whereEqualTo("donorDisplayId", donorId).get().addOnSuccessListener(snapshots -> {
                String foundUid = null;
                if (snapshots != null && !snapshots.isEmpty()) {
                    foundUid = snapshots.getDocuments().get(0).getId();
                } else {
                    Cursor donorCursor = dbHelper.getDonorById(donorId);
                    if (donorCursor != null && donorCursor.moveToFirst()) {
                        String uidCol = donorCursor.getString(donorCursor.getColumnIndexOrThrow("donorUid"));
                        if (uidCol != null && !uidCol.trim().isEmpty()) {
                            foundUid = uidCol.trim();
                        }
                        donorCursor.close();
                    }
                }

                if (foundUid == null || foundUid.isEmpty()) {
                    showSnackbar("Invalid Donor ID: No registered donor profile mapped to a real Firebase account.");
                    return;
                }

                executeDonationTransaction(donorId, foundUid, name, bloodGroup, finalVolume, finalHb, btnAdd);
            }).addOnFailureListener(e -> showSnackbar("Error verifying Donor ID: " + e.getMessage()));
        });

        // FAB to Add Blood Request Popup
        fabAddRequest.setOnClickListener(v -> showAddBloodRequestDialog());

        // FAB to Organise Camp Popup
        fabOrganiseCamp.setOnClickListener(v -> showOrganiseCampDialog());

        // Listen for Published Camps from Firestore
        listenForCamps();

        // Bottom Navigation Switcher
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_donation) {
                layoutDonation.setVisibility(View.VISIBLE);
                layoutRequest.setVisibility(View.GONE);
                layoutOrganise.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.nav_request) {
                layoutDonation.setVisibility(View.GONE);
                layoutRequest.setVisibility(View.VISIBLE);
                layoutOrganise.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.nav_organise) {
                layoutDonation.setVisibility(View.GONE);
                layoutRequest.setVisibility(View.GONE);
                layoutOrganise.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });

        // Default tab on Staff login
        bottomNavigationView.setSelectedItemId(R.id.nav_donation);
    }

    private void executeDonationTransaction(int donorId, String realDonorUid, String name, String bloodGroup, int bloodVolume, double hb, Button btnAdd) {
        long currentTimestamp = System.currentTimeMillis();
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(currentTimestamp));

        String actualCampId = selectedCampId != null ? selectedCampId : "DIRECT_DONATION";
        String actualCampName = selectedCampId != null ? "Selected Camp" : "Direct Center Donation";

        String idempotencyKey = "DONATION_" + realDonorUid + "_" + todayDate;

        btnAdd.setEnabled(false);
        showSnackbar("Processing atomic donation transaction...");

        DocumentReference recordRef = db.collection("donationRecords").document(idempotencyKey);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(recordRef);
            if (snapshot.exists()) {
                throw new FirebaseFirestoreException(
                        "Duplicate Submission: A donation has already been recorded for this donor today (" + todayDate + ").",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS
                );
            }

            Map<String, Object> recMap = new HashMap<>();
            recMap.put("recordId", idempotencyKey);
            recMap.put("donorUid", realDonorUid);
            recMap.put("donorDisplayId", donorId);
            recMap.put("donorName", name);
            recMap.put("campId", actualCampId);
            recMap.put("campName", actualCampName);
            recMap.put("staffUid", currentStaffUid);
            recMap.put("donationDate", todayDate);
            recMap.put("bloodVolume", String.valueOf(bloodVolume));
            recMap.put("volumeUnit", "ml");
            recMap.put("hemoglobin", String.valueOf(hb));
            recMap.put("bloodGroup", bloodGroup);
            recMap.put("status", "COMPLETED");
            recMap.put("timestamp", currentTimestamp);

            transaction.set(recordRef, recMap);
            return null;
        }).addOnSuccessListener(aVoid -> {
            dbHelper.updateDonationStats(donorId, bloodVolume + " ml", String.valueOf(hb));
            dbHelper.insertDonationRecord(idempotencyKey, realDonorUid, donorId, actualCampId, currentStaffUid, todayDate, String.valueOf(bloodVolume), "ml", String.valueOf(hb), bloodGroup, "COMPLETED", currentTimestamp);

            showSnackbar("Donation record successfully submitted!");
            refreshList();
            clearForm();
            btnAdd.setEnabled(true);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Atomic donation transaction failed", e);
            showSnackbar("Transaction Error: " + e.getMessage());
            btnAdd.setEnabled(true);
        });
    }

    private void listenForCamps() {
        campsListener = db.collection("camps")
                .whereEqualTo("status", "PUBLISHED")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    if (containerCamps.getChildCount() > 1) {
                        containerCamps.removeViews(1, containerCamps.getChildCount() - 1);
                    }
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String campId = doc.getId();
                        String name = doc.getString("campName");
                        String loc = doc.getString("location");
                        String date = doc.getString("date");
                        String time = doc.getString("timePeriod");
                        if (name != null) {
                            addCampCard(campId, "🏥 " + name, "📍 Location: " + loc + "\n📅 Date: " + date + "\n⏰ Time: " + time);
                        }
                    }
                });
    }

    private void markRequestCompleted(MaterialCardView card, MaterialButton btnComplete, String completedTitle) {
        if (card.getParent() != null) {
            containerOngoingRequests.removeView(card);
        }

        LinearLayout contentLayout = (LinearLayout) card.getChildAt(0);
        if (contentLayout != null) {
            TextView tvTitle = (TextView) contentLayout.getChildAt(0);
            if (tvTitle != null) {
                tvTitle.setText(completedTitle);
                tvTitle.setTextColor(Color.parseColor("#2E7D32"));
            }
            if (btnComplete != null) {
                contentLayout.removeView(btnComplete);
            }
        }

        containerCompletedRequests.addView(card, 0);
        showSnackbar("Blood Request marked as COMPLETED!");
    }



    private void showOrganiseCampDialog() {
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);

        EditText etLocation = new EditText(this);
        etLocation.setHint(R.string.hint_location);

        EditText etCampName = new EditText(this);
        etCampName.setHint(R.string.hint_camp_name);

        EditText etDate = new EditText(this);
        etDate.setHint(R.string.hint_date);
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                etDate.setText(dateStr);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        EditText etTime = new EditText(this);
        etTime.setHint(R.string.hint_time_period);
        etTime.setFocusable(false);
        etTime.setClickable(true);
        etTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d %s",
                        hourOfDay % 12 == 0 ? 12 : hourOfDay % 12,
                        minute,
                        hourOfDay >= 12 ? "PM" : "AM");
                etTime.setText(timeStr);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        });

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        container.addView(etLocation);
        container.addView(etCampName);
        container.addView(etDate);
        container.addView(etTime);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.organise_camp_title)
                .setView(container)
                .setPositiveButton(R.string.btn_organise_camp, (dialog, which) -> {
                    String loc = etLocation.getText().toString().trim();
                    String name = etCampName.getText().toString().trim();
                    String date = etDate.getText().toString().trim();
                    String time = etTime.getText().toString().trim();

                    if (loc.isEmpty() || name.isEmpty() || date.isEmpty() || time.isEmpty()) {
                        showSnackbar("Please fill all camp details");
                        return;
                    }

                    String campId = "CAMP_" + System.currentTimeMillis();
                    Map<String, Object> campMap = new HashMap<>();
                    campMap.put("campId", campId);
                    campMap.put("campName", name);
                    campMap.put("location", loc);
                    campMap.put("date", date);
                    campMap.put("timePeriod", time);
                    campMap.put("status", "PUBLISHED");
                    campMap.put("organizerStaffUid", currentStaffUid);
                    campMap.put("createdAt", System.currentTimeMillis());

                    db.collection("camps").document(campId).set(campMap)
                            .addOnSuccessListener(aVoid -> showSnackbar("Camp published to Firestore!"))
                            .addOnFailureListener(e -> showSnackbar("Firestore Error: " + e.getMessage()));

                    addCampCard(campId, "🏥 " + name, "📍 Location: " + loc + "\n📅 Date: " + date + "\n⏰ Time: " + time);
                    showSnackbar(getString(R.string.organise_success_title));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addCampCard(String campId, String title, String desc) {
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        MaterialCardView card = createCardContainer();

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(Color.parseColor("#212121"));

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(13);
        tvDesc.setTextColor(Color.parseColor("#616161"));
        tvDesc.setPadding(0, 8, 0, 0);

        cardContent.addView(tvTitle);
        cardContent.addView(tvDesc);
        card.addView(cardContent);

        card.setOnClickListener(v -> {
            selectedCampId = campId;
            showSnackbar("Selected " + title + " for donations.");
        });

        containerCamps.addView(card, 1);
    }

    private void showAddBloodRequestDialog() {
        Spinner spBloodType = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBloodType.setAdapter(adapter);

        EditText etHospital = new EditText(this);
        etHospital.setHint("Hospital / Contact Details");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        final int finalPaddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(finalPaddingPx, finalPaddingPx, finalPaddingPx, finalPaddingPx);
        container.addView(spBloodType);
        container.addView(etHospital);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Which Blood Type is required?")
                .setView(container)
                .setPositiveButton("Create Request", (dialog, which) -> {
                    String selectedBg = spBloodType.getSelectedItem().toString();
                    String hospitalInput = etHospital.getText().toString().trim();
                    String details = hospitalInput.isEmpty() ? "Emergency Ward - City Hospital" : hospitalInput;

                    String requestId = "REQ_" + System.currentTimeMillis();
                    Map<String, Object> reqMap = new HashMap<>();
                    reqMap.put("requestId", requestId);
                    reqMap.put("bloodGroup", selectedBg);
                    reqMap.put("hospital", details);
                    reqMap.put("status", "ONGOING");
                    reqMap.put("staffUid", currentStaffUid);
                    reqMap.put("createdAt", System.currentTimeMillis());

                    db.collection("bloodRequests").document(requestId).set(reqMap)
                            .addOnSuccessListener(aVoid -> {
                                addBloodRequestCard(requestId, selectedBg, details, finalPaddingPx);
                                showSnackbar("Blood Request Published to Firestore!");
                            })
                            .addOnFailureListener(e -> showSnackbar("Firestore Request Error: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addBloodRequestCard(String requestId, String selectedBg, String hospitalDetails, int paddingPx) {
        MaterialCardView requestCard = createCardContainer();
        LinearLayout cardContent = createCardContent(selectedBg, hospitalDetails, paddingPx);

        MaterialButton btnComplete = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnComplete.setText(R.string.btn_mark_completed);
        btnComplete.setTextSize(13);

        cardContent.addView(btnComplete);
        requestCard.addView(cardContent);

        btnComplete.setOnClickListener(v -> {
            db.collection("bloodRequests").document(requestId).update("status", "COMPLETED")
                    .addOnSuccessListener(aVoid -> markRequestCompleted(requestCard, btnComplete, "✅ Urgent " + selectedBg + " Blood Required (COMPLETED)"))
                    .addOnFailureListener(e -> showSnackbar("Error completing request: " + e.getMessage()));
        });

        containerOngoingRequests.addView(requestCard, 0);
    }

    private MaterialCardView createCardContainer() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(cardParams);
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(3 * getResources().getDisplayMetrics().density);
        return card;
    }

    private LinearLayout createCardContent(String selectedBg, String hospitalDetails, int paddingPx) {
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.request_created_title, selectedBg));
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(Color.parseColor("#C62828"));

        TextView tvDesc = new TextView(this);
        tvDesc.setText(getString(R.string.request_created_hospital, hospitalDetails));
        tvDesc.setTextSize(13);
        tvDesc.setTextColor(Color.parseColor("#616161"));

        cardContent.addView(tvTitle);
        cardContent.addView(tvDesc);
        return cardContent;
    }

    private void showSnackbar(String message) {
        View coordinatorLayout = findViewById(R.id.coordinatorLayout);
        if (coordinatorLayout != null) {
            Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void refreshList() {
        idList.clear();
        nameList.clear();
        ageList.clear();
        bloodGroupList.clear();

        Cursor cursor = dbHelper.getAllDonors();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String bloodDonated = cursor.getString(cursor.getColumnIndexOrThrow("bloodDonated"));
                if (bloodDonated != null && !bloodDonated.trim().isEmpty()) {
                    String bloodGroup = cursor.getString(cursor.getColumnIndexOrThrow("bloodGroup"));
                    if ("All".equalsIgnoreCase(selectedFilterBloodGroup) || bloodGroup.equalsIgnoreCase(selectedFilterBloodGroup)) {
                        idList.add(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                        nameList.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                        ageList.add(cursor.getInt(cursor.getColumnIndexOrThrow("age")));
                        bloodGroupList.add(bloodGroup);
                    }
                }
            }
            cursor.close();
        }

        CustomAdapter customAdapter = new CustomAdapter(this, idList, nameList, ageList, bloodGroupList);
        lvDonors.setAdapter(customAdapter);

        if (idList.isEmpty()) {
            showSnackbar(getString(R.string.no_donations_today));
        }
    }

    private void showAnalyticsDialog() {
        showSnackbar("Fetching real-time staff dashboard analytics...");

        // Query Firestore collections asynchronously
        db.collection("donors").get().addOnSuccessListener(donorsSnap -> {
            int totalDonors = donorsSnap.size();
            Map<String, Integer> donorBloodGroups = new HashMap<>();
            for (String bg : bloodGroups) {
                donorBloodGroups.put(bg, 0);
            }
            for (DocumentSnapshot doc : donorsSnap.getDocuments()) {
                String bg = doc.getString("bloodGroup");
                if (bg != null && donorBloodGroups.containsKey(bg)) {
                    donorBloodGroups.put(bg, donorBloodGroups.getOrDefault(bg, 0) + 1);
                }
            }

            db.collection("donationRecords").get().addOnSuccessListener(donationsSnap -> {
                int totalDonations = donationsSnap.size();
                int totalVolumeMl = 0;
                for (DocumentSnapshot doc : donationsSnap.getDocuments()) {
                    String volStr = doc.getString("bloodVolume");
                    try {
                        if (volStr != null) {
                            totalVolumeMl += Integer.parseInt(volStr.replaceAll("[^0-9]", ""));
                        }
                    } catch (Exception ignored) {}
                }
                final int finalVolumeMl = totalVolumeMl;
                double totalLiters = finalVolumeMl / 1000.0;

                db.collection("bloodRequests").whereEqualTo("status", "ONGOING").get().addOnSuccessListener(requestsSnap -> {
                    int activeRequests = requestsSnap.size();

                    db.collection("camps").whereEqualTo("status", "PUBLISHED").get().addOnSuccessListener(campsSnap -> {
                        int tempCamps = 0;
                        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        for (DocumentSnapshot doc : campsSnap.getDocuments()) {
                            String campDate = doc.getString("date");
                            if (campDate != null && campDate.compareTo(todayStr) >= 0) {
                                tempCamps++;
                            }
                        }
                        final int upcomingCamps = tempCamps;

                        db.collection("campRegistrations").get().addOnSuccessListener(regsSnap -> {
                            int campRegs = regsSnap.size();

                            StringBuilder sb = new StringBuilder();
                            sb.append("📊 Staff Analytics Dashboard\n\n");
                            sb.append("• Total Registered Donors: ").append(totalDonors).append("\n");
                            sb.append("• Total Recorded Donations: ").append(totalDonations).append("\n");
                            sb.append("• Total Volume Collected: ").append(String.format(Locale.getDefault(), "%.2f Liters", totalLiters)).append("\n");
                            sb.append("• Active Blood Requests (ONGOING): ").append(activeRequests).append("\n");
                            sb.append("• Published Upcoming Camps: ").append(upcomingCamps).append("\n");
                            sb.append("• Camp Registrations: ").append(campRegs).append("\n\n");
                            sb.append("🩸 Donor Blood Group Breakdown:\n");
                            for (Map.Entry<String, Integer> entry : donorBloodGroups.entrySet()) {
                                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" donors\n");
                            }

                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("Analytics Dashboard")
                                    .setMessage(sb.toString())
                                    .setPositiveButton("Refresh", (d, w) -> showAnalyticsDialog())
                                    .setNegativeButton("Close", null)
                                    .show();

                        }).addOnFailureListener(err -> showAnalyticsDialogFallback(totalDonors, totalDonations, totalLiters, activeRequests, upcomingCamps, donorBloodGroups));
                    }).addOnFailureListener(err -> showAnalyticsDialogFallback(totalDonors, totalDonations, totalLiters, activeRequests, 0, donorBloodGroups));
                }).addOnFailureListener(err -> showAnalyticsDialogFallback(totalDonors, totalDonations, totalLiters, 0, 0, donorBloodGroups));
            }).addOnFailureListener(err -> showAnalyticsDialogFallback(totalDonors, 0, 0.0, 0, 0, donorBloodGroups));
        }).addOnFailureListener(e -> showAnalyticsDialogFallback(0, 0, 0.0, 0, 0, new HashMap<>()));
    }

    private void showAnalyticsDialogFallback(int donors, int donations, double liters, int requests, int camps, Map<String, Integer> bgMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Staff Analytics Dashboard (Partial / Offline):\n\n");
        sb.append("• Total Registered Donors: ").append(donors).append("\n");
        sb.append("• Total Recorded Donations: ").append(donations).append("\n");
        sb.append("• Total Volume Collected: ").append(String.format(Locale.getDefault(), "%.2f Liters", liters)).append("\n");
        sb.append("• Active Blood Requests: ").append(requests).append("\n");
        sb.append("• Published Upcoming Camps: ").append(camps).append("\n\n");
        sb.append("🩸 Blood Group Breakdown:\n");
        if (bgMap != null && !bgMap.isEmpty()) {
            for (Map.Entry<String, Integer> entry : bgMap.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" donors\n");
            }
        } else {
            sb.append("  - Data unavailable offline\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Analytics Dashboard")
                .setMessage(sb.toString())
                .setPositiveButton("Refresh", (d, w) -> showAnalyticsDialog())
                .setNegativeButton("Close", null)
                .show();
    }

    private void clearForm() {
        etDonorId.setText("");
        etDonorName.setText("");
        etDonorAge.setText("");
        etBloodGroupPlaceholder.setText("");
        etBloodDonated.setText("");
        etHemoglobin.setText("");
    }

    private void showProfileDialog() {
        if (currentStaffUid == null || currentStaffUid.isEmpty()) {
            showProfileDialogFallback();
            return;
        }

        db.collection("staffs").document(currentStaffUid).get().addOnSuccessListener(doc -> {
            String name = "Not provided";
            String email = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "Not provided";
            String org = "Not provided";
            String gender = "Not provided";
            String mobile = "Not provided";

            if (doc != null && doc.exists()) {
                String n = doc.getString("fullName");
                if (n == null || n.isEmpty()) n = doc.getString("name");
                if (n != null && !n.isEmpty()) name = n;

                String em = doc.getString("email");
                if (em != null && !em.isEmpty()) email = em;

                String o = doc.getString("organisation");
                if (o == null || o.isEmpty()) o = doc.getString("org");
                if (o != null && !o.isEmpty()) org = o;

                String gen = doc.getString("gender");
                if (gen != null && !gen.isEmpty()) gender = gen;

                String ph = doc.getString("phone");
                if (ph == null || ph.isEmpty()) ph = doc.getString("mobile");
                if (ph != null && !ph.isEmpty()) mobile = ph;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Staff Profile")
                    .setMessage("Name: " + name + "\nEmail: " + email + "\nOrganisation: " + org + "\nGender: " + gender + "\nMobile: " + mobile)
                    .setPositiveButton("OK", null)
                    .show();
        }).addOnFailureListener(e -> showProfileDialogFallback());
    }

    private void showProfileDialogFallback() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name = prefs.getString("name", "Not provided");
        String email = prefs.getString("email", mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "Not provided");
        String org = prefs.getString("org", "Not provided");
        String gender = prefs.getString("gender", "Not provided");
        String mobile = prefs.getString("mobile", "Not provided");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Staff Profile")
                .setMessage("Name: " + name + "\nEmail: " + email + "\nOrganisation: " + org + "\nGender: " + gender + "\nMobile: " + mobile)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    com.example.bloodbank.data.repository.AuthRepository.getInstance(this).signOut(this, () -> {
                        Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
                        startActivity(intent);
                        finishAffinity();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBannerPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Gallery");
        popup.getMenu().add("Camera");
        popup.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (title != null && "Gallery".contentEquals(title)) {
                galleryLauncher.launch("image/*");
                return true;
            } else if (title != null && "Camera".contentEquals(title)) {
                cameraLauncher.launch(null);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (campsListener != null) campsListener.remove();
    }

    // Context Menu for ListView items
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Firestore real-time collections are fully reactive
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) return super.onContextItemSelected(item);

        int position = info.position;
        if (position >= idList.size()) return super.onContextItemSelected(item);

        int donorId = idList.get(position);
        String name = nameList.get(position);
        int age = ageList.get(position);
        String bloodGroup = bloodGroupList.get(position);

        switch (item.getItemId()) {
            case 1: // Select
                etDonorId.setText(String.valueOf(donorId));
                return true;

            case 2: // Details
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Donor Details")
                        .setMessage("ID: " + donorId + "\nName: " + name + "\nAge: " + age + "\nBlood Group: " + bloodGroup)
                        .setPositiveButton("OK", null)
                        .show();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    // Options Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_analytics) {
            showAnalyticsDialog();
            return true;
        } else if (id == R.id.menu_profile) {
            showProfileDialog();
            return true;
        } else if (id == R.id.menu_logout) {
            showLogoutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}