package com.example.bloodbank;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "blood_request_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("donors").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean optIn = doc.getBoolean("optInNotifications");
                    if (Objects.equals(Boolean.TRUE, optIn)) {
                        Map<String, Object> tokenMap = new HashMap<>();
                        tokenMap.put("fcmToken", token);
                        db.collection("donors").document(uid).update(tokenMap);
                    }
                }
            });
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        String notifTitle = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getTitle() : null;
        String notifBody = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getBody() : null;
        String requestId = null;

        if (!remoteMessage.getData().isEmpty()) {
            requestId = remoteMessage.getData().get("requestId");
            if (notifTitle == null) notifTitle = remoteMessage.getData().get("title");
            if (notifBody == null) notifBody = remoteMessage.getData().get("body");
        }

        showNotification(notifTitle, notifBody, requestId);
    }

    private void showNotification(String title, String message, String requestId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Blood Request Alerts", NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);

        Intent intent = new Intent(this, DonorActivity.class);
        if (requestId != null) {
            intent.putExtra("requestId", requestId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_donor_heart)
                .setContentTitle(title != null ? title : "Blood Request Alert")
                .setContentText(message != null ? message : "Urgent blood donation required.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}