package com.example.bloodbank;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class StaffAuthActivity extends AppCompatActivity {

    private EditText etStaffName, etStaffEmail, etStaffOrg, etStaffMobile;
    private final String[] genders = {"Male", "Female", "Other"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_auth);

        MaterialToolbar toolbar = findViewById(R.id.toolbarStaffAuth);
        toolbar.setNavigationOnClickListener(v -> finish());

        etStaffName = findViewById(R.id.etStaffName);
        etStaffEmail = findViewById(R.id.etStaffEmail);
        etStaffOrg = findViewById(R.id.etStaffOrg);
        etStaffMobile = findViewById(R.id.etStaffMobile);
        Spinner spStaffGender = findViewById(R.id.spStaffGender);
        Button btnStaffLogin = findViewById(R.id.btnStaffLogin);
        MaterialButton btnStaffGoogleLogin = findViewById(R.id.btnStaffGoogleLogin);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStaffGender.setAdapter(adapter);

        btnStaffLogin.setOnClickListener(v -> {
            String name = etStaffName.getText().toString().trim();
            String email = etStaffEmail.getText().toString().trim();
            String org = etStaffOrg.getText().toString().trim();
            String mobile = etStaffMobile.getText().toString().trim();
            String gender = spStaffGender.getSelectedItem() != null ? spStaffGender.getSelectedItem().toString() : "Male";

            if (name.isEmpty() || email.isEmpty() || org.isEmpty() || mobile.isEmpty()) {
                showSnackbar("Please fill all fields");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showSnackbar("Please enter a valid email address");
                return;
            }

            if (!mobile.matches("\\d{10}")) {
                showSnackbar("Mobile number must be exactly 10 digits");
                return;
            }

            // Save Staff Profile Details
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_type", "Staff");
            editor.putString("name", name);
            editor.putString("email", email);
            editor.putString("org", org);
            editor.putString("gender", gender);
            editor.putString("mobile", mobile);
            editor.apply();

            showSnackbar("Staff Login Successful!");
            startActivity(new Intent(StaffAuthActivity.this, MainActivity.class));
            finishAffinity();
        });

        btnStaffGoogleLogin.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_type", "Staff");
            editor.putString("name", "Staff Member (Google)");
            editor.putString("email", "staff.google@bloodbank.org");
            editor.putString("org", "Blood Bank Staff");
            editor.putString("gender", "N/A");
            editor.putString("mobile", "9876543210");
            editor.apply();

            showSnackbar("Google Sign-In Successful!");
            startActivity(new Intent(StaffAuthActivity.this, MainActivity.class));
            finishAffinity();
        });
    }

    private void showSnackbar(String message) {
        View layout = findViewById(R.id.coordinatorLayoutStaff);
        if (layout != null) {
            Snackbar.make(layout, message, Snackbar.LENGTH_SHORT).show();
        }
    }
}