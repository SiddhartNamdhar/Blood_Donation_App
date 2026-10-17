package com.example.bloodbank;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        MaterialCardView cardStaff = findViewById(R.id.cardStaff);
        MaterialCardView cardDonor = findViewById(R.id.cardDonor);

        cardStaff.setOnClickListener(v -> {
            Intent intent = new Intent(OnboardingActivity.this, StaffAuthActivity.class);
            startActivity(intent);
        });

        cardDonor.setOnClickListener(v -> {
            Intent intent = new Intent(OnboardingActivity.this, DonorAuthActivity.class);
            startActivity(intent);
        });
    }
}