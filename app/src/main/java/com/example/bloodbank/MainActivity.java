package com.example.bloodbank;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ImageView imgCampBanner;
    private EditText etDonorId, etDonorName, etDonorAge;
    private Spinner spBloodGroup;
    private ListView lvDonors;

    private DBHelper dbHelper;

    private final ArrayList<Integer> idList = new ArrayList<>();
    private final ArrayList<String> nameList = new ArrayList<>();
    private final ArrayList<Integer> ageList = new ArrayList<>();
    private final ArrayList<String> bloodGroupList = new ArrayList<>();

    private final String[] bloodGroups = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};
    private String selectedFilterBloodGroup = "All";

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                    if (uri != null) {
                        imgCampBanner.setImageURI(uri);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        imgCampBanner.setImageBitmap(bitmap);
                    }
                }
        );

        // Bind Views
        imgCampBanner = findViewById(R.id.imgCampBanner);
        etDonorId = findViewById(R.id.etDonorId);
        etDonorName = findViewById(R.id.etDonorName);
        etDonorAge = findViewById(R.id.etDonorAge);
        spBloodGroup = findViewById(R.id.spBloodGroup);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnEdit = findViewById(R.id.btnEdit);
        Button btnDelete = findViewById(R.id.btnDelete);
        lvDonors = findViewById(R.id.lvDonors);
        ChipGroup chipGroupFilter = findViewById(R.id.chipGroupFilter);
        FloatingActionButton fabClearForm = findViewById(R.id.fabClearForm);

        // Initialize DBHelper
        dbHelper = new DBHelper(this);

        // Setup Blood Group Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBloodGroup.setAdapter(spinnerAdapter);

        // Camp Banner Popup Menu
        imgCampBanner.setOnClickListener(this::showBannerPopupMenu);

        // Setup FloatingActionButton to clear form
        fabClearForm.setOnClickListener(v -> {
            clearForm();
            showSnackbar("Form cleared");
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

        // Load Donors
        refreshList();

        // Tapping a donor fills the form
        lvDonors.setOnItemClickListener((parent, view, position, id) -> {
            if (position < idList.size()) {
                etDonorId.setText(String.valueOf(idList.get(position)));
                etDonorName.setText(nameList.get(position));
                etDonorAge.setText(String.valueOf(ageList.get(position)));
                String bg = bloodGroupList.get(position);
                for (int i = 0; i < bloodGroups.length; i++) {
                    if (bloodGroups[i].equalsIgnoreCase(bg)) {
                        spBloodGroup.setSelection(i);
                        break;
                    }
                }
            }
        });

        // Register ListView for Context Menu
        registerForContextMenu(lvDonors);

        // Add Button
        btnAdd.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            String name = etDonorName.getText().toString().trim();
            String ageStr = etDonorAge.getText().toString().trim();
            String bloodGroup = spBloodGroup.getSelectedItem().toString();

            if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
                showSnackbar("Please fill all fields");
                return;
            }

            int donorId = Integer.parseInt(idStr);
            int age = Integer.parseInt(ageStr);

            boolean success = dbHelper.insertDonor(donorId, name, age, bloodGroup);
            if (success) {
                showSnackbar("Donor added successfully");
                refreshList();
                clearForm();
            } else {
                showSnackbar("Error adding donor (ID may already exist)");
            }
        });

        // Edit Button
        btnEdit.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            String name = etDonorName.getText().toString().trim();
            String ageStr = etDonorAge.getText().toString().trim();
            String bloodGroup = spBloodGroup.getSelectedItem().toString();

            if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
                showSnackbar("Please fill all fields");
                return;
            }

            int donorId = Integer.parseInt(idStr);
            int age = Integer.parseInt(ageStr);

            boolean success = dbHelper.updateDonor(donorId, name, age, bloodGroup);
            if (success) {
                showSnackbar("Donor updated successfully");
                refreshList();
            } else {
                showSnackbar("Donor ID not found for update");
            }
        });

        // Delete Button (asks confirmation in MaterialAlertDialog)
        btnDelete.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            if (idStr.isEmpty()) {
                showSnackbar("Please enter or select a Donor ID to delete");
                return;
            }
            int donorId = Integer.parseInt(idStr);
            showDeleteConfirmationDialog(donorId);
        });
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
                String bloodGroup = cursor.getString(cursor.getColumnIndexOrThrow("bloodGroup"));
                if ("All".equalsIgnoreCase(selectedFilterBloodGroup) || bloodGroup.equalsIgnoreCase(selectedFilterBloodGroup)) {
                    idList.add(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                    nameList.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                    ageList.add(cursor.getInt(cursor.getColumnIndexOrThrow("age")));
                    bloodGroupList.add(bloodGroup);
                }
            }
            cursor.close();
        }

        CustomAdapter customAdapter = new CustomAdapter(this, idList, nameList, ageList, bloodGroupList);
        lvDonors.setAdapter(customAdapter);
    }

    private void clearForm() {
        etDonorId.setText("");
        etDonorName.setText("");
        etDonorAge.setText("");
        spBloodGroup.setSelection(0);
    }

    private void showDeleteConfirmationDialog(int donorId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete donor with ID " + donorId + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    boolean success = dbHelper.deleteDonor(donorId);
                    if (success) {
                        showSnackbar("Donor deleted successfully");
                        refreshList();
                        clearForm();
                    } else {
                        showSnackbar("Donor ID not found");
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showProfileDialog() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name = prefs.getString("name", "Staff Member");
        String email = prefs.getString("email", "staff@bloodbank.org");
        String org = prefs.getString("org", "Blood Bank Staff");
        String gender = prefs.getString("gender", "N/A");
        String mobile = prefs.getString("mobile", "N/A");

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
                    SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
                    startActivity(intent);
                    finishAffinity();
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

    // Context Menu for ListView items
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.lvDonors) {
            menu.setHeaderTitle("Donor Options");
            menu.add(0, 1, 0, "Edit");
            menu.add(0, 2, 0, "Delete");
            menu.add(0, 3, 0, "Details");
        }
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
            case 1: // Edit
                etDonorId.setText(String.valueOf(donorId));
                etDonorName.setText(name);
                etDonorAge.setText(String.valueOf(age));
                for (int i = 0; i < bloodGroups.length; i++) {
                    if (bloodGroups[i].equalsIgnoreCase(bloodGroup)) {
                        spBloodGroup.setSelection(i);
                        break;
                    }
                }
                return true;

            case 2: // Delete
                showDeleteConfirmationDialog(donorId);
                return true;

            case 3: // Details
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