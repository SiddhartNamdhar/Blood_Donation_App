const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Triggers on new Blood Request creation in Cloud Firestore ('bloodRequests/{requestId}').
 * Filters opted-in donors matching the requested blood group and sends multicast FCM notification.
 */
exports.sendBloodRequestNotification = functions.firestore
    .document('bloodRequests/{requestId}')
    .onCreate(async (snapshot, context) => {
        const requestData = snapshot.data();
        const requestId = context.params.requestId;
        const bloodGroup = requestData.bloodGroup;
        const hospital = requestData.hospital || requestData.details || 'City Hospital';

        try {
            // Query donors matching requested blood group AND optInNotifications == true
            const donorsSnapshot = await admin.firestore().collection('donors')
                .where('bloodGroup', '==', bloodGroup)
                .where('optInNotifications', '==', true)
                .get();

            if (donorsSnapshot.empty) {
                console.log('No opted-in donors found for blood group:', bloodGroup);
                return null;
            }

            const tokensMap = new Map(); // Map donor UID -> fcmToken
            donorsSnapshot.forEach(doc => {
                const token = doc.data().fcmToken;
                if (token && token.trim() !== '') {
                    tokensMap.set(doc.id, token);
                }
            });

            const tokens = Array.from(tokensMap.values());
            if (tokens.length === 0) {
                console.log('No valid FCM tokens available.');
                return null;
            }

            const message = {
                notification: {
                    title: `🚨 Urgent ${bloodGroup} Blood Required!`,
                    body: `Hospital: ${hospital}\nTap to view details in Donor Portal.`
                },
                data: {
                    requestId: requestId,
                    bloodGroup: bloodGroup,
                    hospital: hospital
                },
                tokens: tokens
            };

            const response = await admin.messaging().sendEachForMulticast(message);
            console.log(`Multicast sent: ${response.successCount} succeeded, ${response.failureCount} failed.`);

            // Clean up stale or invalid FCM tokens
            if (response.failureCount > 0) {
                const invalidTokens = [];
                response.responses.forEach((resp, idx) => {
                    if (!resp.success) {
                        const error = resp.error;
                        if (error.code === 'messaging/invalid-registration-token' ||
                            error.code === 'messaging/registration-token-not-registered') {
                            invalidTokens.push(tokens[idx]);
                        }
                    }
                });

                if (invalidTokens.length > 0) {
                    for (const [donorUid, token] of tokensMap.entries()) {
                        if (invalidTokens.includes(token)) {
                            await admin.firestore().collection('donors').document(donorUid).update({
                                fcmToken: null
                            });
                        }
                    }
                }
            }

            return null;
        } catch (error) {
            console.error('Error sending blood request notifications:', error);
            return null;
        }
    });