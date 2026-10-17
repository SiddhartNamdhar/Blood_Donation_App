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

public class DonorAuthActivity extends AppCompatActivity {

    private EditText etDonorAuthName, etDonorAuthEmail, etDonorAuthMobile;
    private final String[] genders = {"Male", "Female", "Other"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor_auth);

        MaterialToolbar toolbar = findViewById(R.id.toolbarDonorAuth);
        toolbar.setNavigationOnClickListener(v -> finish());

        etDonorAuthName = findViewById(R.id.etDonorAuthName);
        etDonorAuthEmail = findViewById(R.id.etDonorAuthEmail);
        etDonorAuthMobile = findViewById(R.id.etDonorAuthMobile);
        Spinner spDonorAuthGender = findViewById(R.id.spDonorAuthGender);
        Button btnDonorLogin = findViewById(R.id.btnDonorLogin);
        MaterialButton btnDonorGoogleLogin = findViewById(R.id.btnDonorGoogleLogin);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDonorAuthGender.setAdapter(adapter);

        btnDonorLogin.setOnClickListener(v -> {
            String name = etDonorAuthName.getText().toString().trim();
            String email = etDonorAuthEmail.getText().toString().trim();
            String mobile = etDonorAuthMobile.getText().toString().trim();
            String gender = spDonorAuthGender.getSelectedItem() != null ? spDonorAuthGender.getSelectedItem().toString() : "Male";

            if (name.isEmpty() || email.isEmpty() || mobile.isEmpty()) {
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

            // Save Donor Profile Details
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_type", "Donor");
            editor.putString("name", name);
            editor.putString("email", email);
            editor.putString("mobile", mobile);
            editor.putString("gender", gender);
            editor.apply();

            showSnackbar("Donor Login Successful!");
            startActivity(new Intent(DonorAuthActivity.this, DonorActivity.class));
            finishAffinity();
        });

        btnDonorGoogleLogin.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_type", "Donor");
            editor.putString("name", "Valued Donor (Google)");
            editor.putString("email", "donor.google@bloodbank.org");
            editor.putString("mobile", "9876543210");
            editor.putString("gender", "N/A");
            editor.apply();

            showSnackbar("Google Sign-In Successful!");
            startActivity(new Intent(DonorAuthActivity.this, DonorActivity.class));
            finishAffinity();
        });
    }

    private void showSnackbar(String message) {
        View layout = findViewById(R.id.coordinatorLayoutDonorAuth);
        if (layout != null) {
            Snackbar.make(layout, message, Snackbar.LENGTH_SHORT).show();
        }
    }
}