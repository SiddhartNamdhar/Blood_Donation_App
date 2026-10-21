package com.example.bloodbank;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bloodbank.data.model.DonorProfile;
import com.example.bloodbank.data.model.StaffProfile;
import com.example.bloodbank.data.repository.AuthRepository;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View splashContent = findViewById(R.id.layoutSplashContent);
        if (splashContent != null) {
            splashContent.setAlpha(0f);
            splashContent.setScaleX(0.92f);
            splashContent.setScaleY(0.92f);
            splashContent.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(700)
                    .start();
        }

        AuthRepository authRepository = AuthRepository.getInstance(this);

        authRepository.checkActiveSession(new AuthRepository.SessionCheckCallback() {
            @Override
            public void onAuthenticatedStaff(StaffProfile staffProfile) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onAuthenticatedDonor(DonorProfile donorProfile) {
                Intent intent = new Intent(SplashActivity.this, DonorActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onUnauthenticated() {
                Intent intent = new Intent(SplashActivity.this, OnboardingActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}