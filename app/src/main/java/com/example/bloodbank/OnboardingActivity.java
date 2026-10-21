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

        if (cardStaff != null) {
            cardStaff.setAlpha(0f);
            cardStaff.setTranslationY(40f);
            cardStaff.animate().alpha(1f).translationY(0f).setDuration(450).setStartDelay(100).start();
        }

        if (cardDonor != null) {
            cardDonor.setAlpha(0f);
            cardDonor.setTranslationY(40f);
            cardDonor.animate().alpha(1f).translationY(0f).setDuration(450).setStartDelay(200).start();
        }

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