package com.example.bloodbank;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bloodbank.data.repository.AuthRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DonorActivity extends AppCompatActivity {

    private static final String TAG = "DonorActivity";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private FirebaseFirestore db;
    private ListenerRegistration campsListener;
    private ListenerRegistration recordsListener;

    private TextView tvDonorWelcome;
    private TextView tvScreeningStatus;
    private TextView tvCampScheduleInfo;
    private MaterialButton btnRegisterCamp;
    private MaterialSwitch switchNotifications;
    private LinearLayout containerDonorHistory;

    private final Map<String, DocumentSnapshot> publishedCampsMap = new HashMap<>();
    private String selectedCampId = null;
    private String selectedCampTitle = null;

    private int donorDisplayId = 0;
    private String currentUid = "";
    private String donorName = "";
    private String bloodGroup = "O+";
    private String currentScreeningStatus = "PENDING_SCREENING";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor);

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Intent intent = new Intent(DonorActivity.this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        currentUid = currentUser.getUid();

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        donorName = prefs.getString("name", "Donor");
        bloodGroup = prefs.getString("bloodGroup", "O+");
        donorDisplayId = prefs.getInt("donor_id", 0);

        initViews();
        loadAuthoritativeProfile(prefs);
        checkNotificationPermission();
        setupNotificationSwitch();
        setupScreeningStatusListener();
        setupCalendarListener();

        listenForPublishedCamps();
        listenForDonationRecords();
    }

    private void initViews() {
        MaterialToolbar toolbarDonor = findViewById(R.id.toolbarDonor);
        setSupportActionBar(toolbarDonor);
        if (toolbarDonor.getOverflowIcon() != null) {
            toolbarDonor.getOverflowIcon().setTint(Color.WHITE);
        }

        tvDonorWelcome = findViewById(R.id.tvDonorWelcome);
        tvScreeningStatus = findViewById(R.id.tvScreeningStatus);
        tvCampScheduleInfo = findViewById(R.id.tvCampScheduleInfo);
        btnRegisterCamp = findViewById(R.id.btnRegisterCamp);
        switchNotifications = findViewById(R.id.switchNotifications);
        containerDonorHistory = findViewById(R.id.containerDonorHistory);

        String welcomeSuffix = donorDisplayId > 0 ? " (ID: " + donorDisplayId + ")" : "";
        tvDonorWelcome.setText(getString(R.string.donor_welcome_formatted, donorName + welcomeSuffix));

        btnRegisterCamp.setOnClickListener(v -> registerForSelectedCamp());
    }

    private void loadAuthoritativeProfile(SharedPreferences prefs) {
        db.collection("donors").document(currentUid).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                String fetchedName = doc.getString("fullName");
                if (fetchedName == null || fetchedName.isEmpty()) fetchedName = doc.getString("name");
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    donorName = fetchedName;
                    prefs.edit().putString("name", donorName).apply();
                }

                String fetchedBg = doc.getString("bloodGroup");
                if (fetchedBg != null && !fetchedBg.isEmpty()) {
                    bloodGroup = fetchedBg;
                    prefs.edit().putString("bloodGroup", bloodGroup).apply();
                }

                Long idLong = doc.getLong("donorDisplayId");
                if (idLong != null && idLong > 0) {
                    donorDisplayId = idLong.intValue();
                    prefs.edit().putInt("donor_id", donorDisplayId).apply();
                }

                Boolean optIn = doc.getBoolean("optInNotifications");
                if (switchNotifications != null) {
                    switchNotifications.setChecked(Boolean.TRUE.equals(optIn));
                }

                String scr = doc.getString("screeningStatus");
                if (scr != null) {
                    currentScreeningStatus = scr;
                }

                tvDonorWelcome.setText(getString(R.string.donor_welcome_formatted, donorName + " (ID: " + donorDisplayId + ")"));

                // Sync local SQLite cache for offline queries
                DBHelper dbHelper = new DBHelper(this);
                dbHelper.insertDonorFull(donorDisplayId, currentUid, donorName,
                        prefs.getInt("age", 22), bloodGroup,
                        doc.getString("email") != null ? doc.getString("email") : "",
                        doc.getString("phone") != null ? doc.getString("phone") : (doc.getString("mobile") != null ? doc.getString("mobile") : ""),
                        doc.getString("gender") != null ? doc.getString("gender") : getString(R.string.profile_not_provided),
                        currentScreeningStatus);
            }
        }).addOnFailureListener(e -> Log.w(TAG, "Authoritative profile fetch error: " + e.getMessage()));
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void setupNotificationSwitch() {
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!currentUid.isEmpty()) {
                Map<String, Object> optMap = new HashMap<>();
                optMap.put("optInNotifications", isChecked);
                if (isChecked) {
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            optMap.put("fcmToken", task.getResult());
                        }
                        db.collection("donors").document(currentUid).update(optMap);
                    });
                } else {
                    optMap.put("fcmToken", null);
                    db.collection("donors").document(currentUid).update(optMap);
                }
                Toast.makeText(this, isChecked ? getString(R.string.toast_notifications_enabled) : getString(R.string.toast_notifications_disabled), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupScreeningStatusListener() {
        if (!currentUid.isEmpty()) {
            db.collection("donors").document(currentUid).addSnapshotListener((doc, e) -> {
                if (doc != null && doc.exists()) {
                    String status = doc.getString("screeningStatus");
                    if (status != null) {
                        currentScreeningStatus = status;
                    }
                    updateScreeningUI(currentScreeningStatus);
                } else {
                    updateScreeningUI("PENDING_SCREENING");
                }
            });
        }
    }

    private void setupCalendarListener() {
        CalendarView calendarViewCamps = findViewById(R.id.calendarViewCamps);
        calendarViewCamps.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String selectedDateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            checkCampForDate(selectedDateStr);
        });
    }

    private void updateScreeningUI(String status) {
        if ("CLEARED".equalsIgnoreCase(status)) {
            tvScreeningStatus.setText(R.string.donor_status_eligible);
            tvScreeningStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else if ("DEFERRED".equalsIgnoreCase(status)) {
            tvScreeningStatus.setText(R.string.donor_status_deferred);
            tvScreeningStatus.setTextColor(Color.parseColor("#C62828"));
        } else {
            tvScreeningStatus.setText(R.string.status_pending_screening);
            tvScreeningStatus.setTextColor(Color.parseColor("#E65100"));
        }
    }

    private void listenForPublishedCamps() {
        campsListener = db.collection("camps")
                .whereEqualTo("status", "PUBLISHED")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen camps failed", e);
                        return;
                    }
                    if (snapshots != null) {
                        publishedCampsMap.clear();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            publishedCampsMap.put(doc.getId(), doc);
                        }
                    }
                });
    }

    private void checkCampForDate(String dateStr) {
        selectedCampId = null;
        selectedCampTitle = null;
        List<DocumentSnapshot> matchingCamps = new ArrayList<>();

        for (DocumentSnapshot doc : publishedCampsMap.values()) {
            String campDate = doc.getString("date");
            if (campDate != null && campDate.contains(dateStr)) {
                matchingCamps.add(doc);
            }
        }

        if (matchingCamps.isEmpty()) {
            tvCampScheduleInfo.setText(getString(R.string.camp_no_active_for_date, dateStr));
            btnRegisterCamp.setVisibility(View.GONE);
        } else if (matchingCamps.size() == 1) {
            DocumentSnapshot doc = matchingCamps.get(0);
            selectedCampId = doc.getId();
            selectedCampTitle = doc.getString("campName");
            String location = doc.getString("location");
            String timePeriod = doc.getString("timePeriod");

            tvCampScheduleInfo.setText(getString(R.string.camp_schedule_details,
                    selectedCampTitle != null ? selectedCampTitle : "",
                    location != null ? location : "",
                    timePeriod != null ? timePeriod : ""));
            btnRegisterCamp.setVisibility(View.VISIBLE);
        } else {
            String[] campNames = new String[matchingCamps.size()];
            for (int i = 0; i < matchingCamps.size(); i++) {
                campNames[i] = matchingCamps.get(i).getString("campName");
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.dialog_multiple_camps_title, dateStr))
                    .setItems(campNames, (dialog, which) -> {
                        DocumentSnapshot doc = matchingCamps.get(which);
                        selectedCampId = doc.getId();
                        selectedCampTitle = doc.getString("campName");
                        String location = doc.getString("location");
                        String timePeriod = doc.getString("timePeriod");

                        tvCampScheduleInfo.setText(getString(R.string.camp_schedule_details,
                                selectedCampTitle != null ? selectedCampTitle : "",
                                location != null ? location : "",
                                timePeriod != null ? timePeriod : ""));
                        btnRegisterCamp.setVisibility(View.VISIBLE);
                    })
                    .show();
        }
    }

    private void registerForSelectedCamp() {
        if (selectedCampId == null || currentUid.isEmpty()) {
            Toast.makeText(this, R.string.toast_select_camp_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String regId = selectedCampId + "_" + currentUid;

        db.collection("campRegistrations").document(regId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_already_registered_title)
                        .setMessage(getString(R.string.dialog_already_registered_msg, selectedCampTitle))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                Map<String, Object> regMap = new HashMap<>();
                regMap.put("registrationId", regId);
                regMap.put("campId", selectedCampId);
                regMap.put("campTitle", selectedCampTitle);
                regMap.put("donorUid", currentUid);
                regMap.put("donorDisplayId", donorDisplayId);
                regMap.put("donorName", donorName);
                regMap.put("bloodGroup", bloodGroup);
                regMap.put("status", "REGISTERED");
                regMap.put("registeredAt", System.currentTimeMillis());

                db.collection("campRegistrations").document(regId).set(regMap)
                        .addOnSuccessListener(aVoid -> new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.dialog_registration_confirmed_title)
                                .setMessage(getString(R.string.dialog_registration_confirmed_msg, selectedCampTitle))
                                .setPositiveButton(android.R.string.ok, null)
                                .show())
                        .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.toast_registration_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).addOnFailureListener(e -> Toast.makeText(this, getString(R.string.toast_verification_error, e.getMessage()), Toast.LENGTH_SHORT).show());
    }

    private void listenForDonationRecords() {
        if (currentUid.isEmpty()) return;

        recordsListener = db.collection("donationRecords")
                .whereEqualTo("donorUid", currentUid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen records failed", e);
                        return;
                    }

                    containerDonorHistory.removeAllViews();
                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String date = doc.getString("donationDate");
                            String campName = doc.getString("campName");
                            String volume = doc.getString("bloodVolume");
                            String unit = doc.getString("volumeUnit");
                            String hb = doc.getString("hemoglobin");

                            addHistoryCard(date, campName, hb, volume + " " + (unit != null ? unit : "ml"));
                        }
                    } else {
                        TextView tvEmpty = new TextView(this);
                        tvEmpty.setText(R.string.donor_history_empty);
                        tvEmpty.setTextSize(13);
                        tvEmpty.setTextColor(Color.parseColor("#757575"));
                        containerDonorHistory.addView(tvEmpty);
                    }
                });
    }

    private void addHistoryCard(String date, String campName, String hb, String volume) {
        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(cardParams);
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2 * getResources().getDisplayMetrics().density);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        TextView tvDate = new TextView(this);
        tvDate.setText(getString(R.string.donor_history_date, (date != null ? date : getString(R.string.not_applicable))));
        tvDate.setTextSize(14);
        tvDate.setTypeface(null, Typeface.BOLD);
        tvDate.setTextColor(Color.parseColor("#212121"));

        TextView tvCamp = new TextView(this);
        tvCamp.setText(getString(R.string.donor_history_location, (campName != null ? campName : getString(R.string.camp_default_location))));
        tvCamp.setTextSize(13);
        tvCamp.setTextColor(Color.parseColor("#C62828"));

        TextView tvStats = new TextView(this);
        tvStats.setText(getString(R.string.donor_history_stats, (hb != null ? hb : "13.8 g/dL"), (volume != null ? volume : "450 ml")));
        tvStats.setTextSize(13);
        tvStats.setTextColor(Color.parseColor("#424242"));
        tvStats.setPadding(0, 4, 0, 0);

        cardContent.addView(tvDate);
        cardContent.addView(tvCamp);
        cardContent.addView(tvStats);
        card.addView(cardContent);

        containerDonorHistory.addView(card);
    }

    private void showProfileDialog() {
        if (currentUid.isEmpty()) {
            Toast.makeText(this, R.string.toast_session_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.toast_loading_profile, Toast.LENGTH_SHORT).show();
        db.collection("donors").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    String notProvided = getString(R.string.profile_not_provided);
                    String name = notProvided;
                    String email = notProvided;
                    String mobile = notProvided;
                    String gender = notProvided;
                    String bg = notProvided;
                    int displayId = donorDisplayId;

                    if (doc != null && doc.exists()) {
                        String n = doc.getString("fullName");
                        if (n == null || n.isEmpty()) n = doc.getString("name");
                        if (n != null && !n.isEmpty()) name = n;

                        String em = doc.getString("email");
                        if (em != null && !em.isEmpty()) email = em;

                        String ph = doc.getString("phone");
                        if (ph == null || ph.isEmpty()) ph = doc.getString("mobile");
                        if (ph != null && !ph.isEmpty()) mobile = ph;

                        String gen = doc.getString("gender");
                        if (gen != null && !gen.isEmpty()) gender = gen;

                        String b = doc.getString("bloodGroup");
                        if (b != null && !b.isEmpty()) bg = b;

                        Long idLong = doc.getLong("donorDisplayId");
                        if (idLong != null && idLong > 0) displayId = idLong.intValue();
                    }

                    String displayIdStr = displayId > 0 ? String.valueOf(displayId) : getString(R.string.profile_not_assigned);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_profile_title)
                            .setMessage(getString(R.string.dialog_profile_msg, displayIdStr, name, email, mobile, gender, bg))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.toast_profile_load_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_logout)
                .setMessage(R.string.dialog_logout_confirm)
                .setPositiveButton(R.string.menu_logout, (dialog, which) -> {
                    AuthRepository.getInstance(this).signOut(() -> {
                        Intent intent = new Intent(DonorActivity.this, OnboardingActivity.class);
                        startActivity(intent);
                        finishAffinity();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (campsListener != null) campsListener.remove();
        if (recordsListener != null) recordsListener.remove();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_profile) {
            showProfileDialog();
            return true;
        } else if (id == R.id.menu_logout) {
            showLogoutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}