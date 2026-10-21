const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const path = require('path');
const fs = require('fs');

const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

if (!fs.existsSync(serviceAccountPath)) {
    console.error('================================================================');
    console.error('ERROR: Service account key file not found at:');
    console.error(serviceAccountPath);
    console.error('');
    console.error('Please download your Service Account Private Key:');
    console.error('1. Open Firebase Console -> Project Settings -> Service Accounts');
    console.error('2. Click "Generate new private key"');
    console.error('3. Save file as "scripts/serviceAccountKey.json"');
    console.error('================================================================');
    process.exit(1);
}

const serviceAccount = require(serviceAccountPath);

const app = initializeApp({
    credential: cert(serviceAccount)
});

const auth = getAuth(app);
const db = getFirestore(app);

async function provisionStaff(identifier, orgName, fullName) {
    console.log(`Starting staff provisioning for: ${identifier}...`);

    let userRecord;
    try {
        if (identifier.includes('@')) {
            userRecord = await auth.getUserByEmail(identifier.trim().toLowerCase());
        } else {
            userRecord = await auth.getUser(identifier.trim());
        }
    } catch (e) {
        console.error(`Failed to find user with identifier '${identifier}':`, e.message);
        process.exit(1);
    }

    const uid = userRecord.uid;
    console.log(`Found Firebase Auth account UID: ${uid} (Email: ${userRecord.email})`);

    // 1. Assign trusted Custom Claim { role: 'Staff' }
    await auth.setCustomUserClaims(uid, {
        role: 'Staff',
        isStaff: true
    });
    console.log(`[SUCCESS] Custom claim { role: 'Staff' } set for UID: ${uid}`);

    // 2. Ensure /staffs/{uid} document exists in Firestore
    const staffDocRef = db.collection('staffs').doc(uid);
    const docSnap = await staffDocRef.get();

    const now = Date.now();
    const resolvedName = fullName || userRecord.displayName || (docSnap.exists ? docSnap.data().fullName || docSnap.data().name : 'Staff Member');
    const resolvedOrg = orgName || (docSnap.exists ? docSnap.data().organisation || docSnap.data().org : 'Central Blood Bank');
    const existingData = docSnap.exists ? docSnap.data() : {};

    const profilePayload = {
        uid: uid,
        fullName: resolvedName,
        name: resolvedName,
        email: userRecord.email || '',
        organisation: resolvedOrg,
        org: resolvedOrg,
        phone: existingData.phone || existingData.mobile || 'Not provided',
        mobile: existingData.mobile || existingData.phone || 'Not provided',
        gender: existingData.gender || 'Not provided',
        createdAt: existingData.createdAt || now,
        updatedAt: now
    };

    // Remove any accidental client role fields in document to preserve strict separation
    delete profilePayload.role;
    delete profilePayload.isStaff;
    delete profilePayload.admin;

    await staffDocRef.set(profilePayload, { merge: true });
    console.log(`[SUCCESS] Firestore document /staffs/${uid} verified and updated.`);
    console.log('----------------------------------------------------------------');
    console.log(`STAFF PROVISIONING COMPLETE!`);
    console.log(`User: ${profilePayload.fullName} (${userRecord.email})`);
    console.log(`Organisation: ${profilePayload.organisation}`);
    console.log(`Staff Role Claim: 'Staff' (VERIFIED)`);
    console.log(`The staff user can now sign in to the BloodBank Android application.`);
    console.log('----------------------------------------------------------------');
}

const args = process.argv.slice(2);
if (args.length === 0) {
    console.log('Usage: node provision_staff.js <EMAIL_OR_UID> [organisation] [full_name]');
    process.exit(1);
}

const target = args[0];
const org = args[1] || 'Central Blood Bank';
const name = args[2] || '';

provisionStaff(target, org, name)
    .then(() => process.exit(0))
    .catch(err => {
        console.error('Fatal Error during provisioning:', err);
        process.exit(1);
    });
