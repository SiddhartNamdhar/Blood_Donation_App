package com.example.bloodbank;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DonorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor);

        MaterialToolbar toolbarDonor = findViewById(R.id.toolbarDonor);
        setSupportActionBar(toolbarDonor);
        if (toolbarDonor.getOverflowIcon() != null) {
            toolbarDonor.getOverflowIcon().setTint(Color.WHITE);
        }
    }

    private void showProfileDialog() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name = prefs.getString("name", "Valued Donor");
        String email = prefs.getString("email", "donor@bloodbank.org");
        String mobile = prefs.getString("mobile", "N/A");
        String gender = prefs.getString("gender", "N/A");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Donor Profile")
                .setMessage("Name: " + name + "\nEmail: " + email + "\nMobile: " + mobile + "\nGender: " + gender)
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
                    Intent intent = new Intent(DonorActivity.this, OnboardingActivity.class);
                    startActivity(intent);
                    finishAffinity();
                })
                .setNegativeButton("Cancel", null)
                .show();
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