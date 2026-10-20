const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'blood-bank-b8f18-test';

async function runTests() {
  console.log('======================================================================');
  console.log('--- STARTING FIRESTORE SECURITY RULES HARDENING VERIFICATION SUITE ---');
  console.log('======================================================================\n');

  const rulesPath = path.resolve(__dirname, '../firestore.rules');
  const rules = fs.readFileSync(rulesPath, 'utf8');

  let testEnv;
  try {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: rules,
        host: '127.0.0.1',
        port: 8080
      }
    });
  } catch (err) {
    console.error('Failed to initialize test environment. Is Firestore emulator running?', err.message);
    process.exit(1);
  }

  let passed = 0;
  let failed = 0;

  async function test(name, fn) {
    try {
      await fn();
      console.log(`  [PASS] ${name}`);
      passed++;
    } catch (err) {
      console.error(`  [FAIL] ${name}: ${err.message}`);
      failed++;
    }
  }

  try {
    // Contexts:
    const unauthed = testEnv.unauthenticatedContext().firestore();
    const donorAlice = testEnv.authenticatedContext('donor_alice', { role: 'Donor' }).firestore();
    const donorBob = testEnv.authenticatedContext('donor_bob', { role: 'Donor' }).firestore();
    const staffSmith = testEnv.authenticatedContext('staff_smith', { role: 'Staff' }).firestore();
    const staffJones = testEnv.authenticatedContext('staff_jones', { role: 'Staff' }).firestore();

    // -------------------------------------------------------------------------
    // CATEGORY 1: Staff Profiles (/staffs/{uid})
    // -------------------------------------------------------------------------
    console.log('--- CATEGORY 1: Staff Profiles (/staffs/{uid}) ---');

    const validStaffProfile = {
      uid: 'staff_smith',
      fullName: 'Dr. John Smith',
      name: 'Dr. John Smith',
      email: 'dr.smith@bloodbank.org',
      organisation: 'City Central Blood Bank',
      org: 'City Central Blood Bank',
      phone: '9876543210',
      mobile: '9876543210',
      gender: 'Male',
      createdAt: 1000,
      updatedAt: 1000
    };

    await test('Staff can create their own valid staff profile', async () => {
      await assertSucceeds(staffSmith.collection('staffs').doc('staff_smith').set(validStaffProfile));
    });

    await test('Staff cannot create profile with unexpected/extra fields', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').set({
        ...validStaffProfile,
        hackerField: 'unauthorized_payload'
      }));
    });

    await test('Staff cannot self-assign role or admin keys during profile create', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').set({
        ...validStaffProfile,
        role: 'Staff',
        isStaff: true
      }));
    });

    await test('Staff can update explicit permitted fields (org, phone, gender, updatedAt)', async () => {
      await assertSucceeds(staffSmith.collection('staffs').doc('staff_smith').update({
        organisation: 'Metro Health Blood Center',
        org: 'Metro Health Blood Center',
        phone: '9123456789',
        gender: 'Male',
        updatedAt: 2000
      }));
    });

    await test('Staff cannot update protected/immutable fields (uid, email, createdAt)', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').update({
        email: 'hacked.email@evil.com',
        createdAt: 9999
      }));
    });

    await test('Staff cannot inject unexpected fields on profile update', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').update({
        secretBackdoor: 'malicious'
      }));
    });

    await test('Staff cannot update with invalid gender value', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').update({
        gender: 'InvalidGender'
      }));
    });

    await test('Staff cannot delete staff profiles', async () => {
      await assertFails(staffSmith.collection('staffs').doc('staff_smith').delete());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 2: Donor Profiles & Display ID Uniqueness, Privacy, & Single-Allocation
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 2: Donor Profiles & Display ID Uniqueness, Privacy, & Anti-Hoarding ---');

    const validAliceProfile = {
      uid: 'donor_alice',
      fullName: 'Alice Smith',
      name: 'Alice Smith',
      email: 'alice@example.com',
      phone: '9876543210',
      mobile: '9876543210',
      age: 26,
      gender: 'Female',
      bloodGroup: 'O+',
      donorDisplayId: 1234,
      optInNotifications: true,
      createdAt: 1000,
      updatedAt: 1000
    };

    await test('Donor Alice can legitimately register using atomic WriteBatch (profile + ID 1234)', async () => {
      const batch = donorAlice.batch();
      batch.set(donorAlice.collection('donorDisplayIds').doc('1234'), {
        donorUid: 'donor_alice',
        assignedAt: 1000
      });
      batch.set(donorAlice.collection('donors').doc('donor_alice'), validAliceProfile);
      await assertSucceeds(batch.commit());
    });

    await test('Reservation privacy: Alice can read her own reservation document', async () => {
      await assertSucceeds(donorAlice.collection('donorDisplayIds').doc('1234').get());
    });

    await test('Reservation privacy: Staff can read Alice reservation document', async () => {
      await assertSucceeds(staffSmith.collection('donorDisplayIds').doc('1234').get());
    });

    await test('Reservation privacy: Donor Bob CANNOT read Alice reservation document', async () => {
      await assertFails(donorBob.collection('donorDisplayIds').doc('1234').get());
    });

    await test('Reservation privacy: Unauthenticated user CANNOT read reservation document', async () => {
      await assertFails(unauthed.collection('donorDisplayIds').doc('1234').get());
    });

    await test('Repeated reservations: Alice CANNOT reserve a second donorDisplayId (1235) standalone', async () => {
      await assertFails(donorAlice.collection('donorDisplayIds').doc('1235').set({
        donorUid: 'donor_alice',
        assignedAt: 1100
      }));
    });

    await test('Hoarding prevention: User CANNOT reserve displayId standalone without profile', async () => {
      await assertFails(donorBob.collection('donorDisplayIds').doc('9999').set({
        donorUid: 'donor_bob',
        assignedAt: 1100
      }));
    });

    await test('Donor Bob CANNOT reserve already-taken donorDisplayId 1234 (Duplicate Rejection)', async () => {
      const batch = donorBob.batch();
      batch.set(donorBob.collection('donorDisplayIds').doc('1234'), {
        donorUid: 'donor_bob',
        assignedAt: 1100
      });
      batch.set(donorBob.collection('donors').doc('donor_bob'), {
        uid: 'donor_bob',
        fullName: 'Bob Brown',
        name: 'Bob Brown',
        email: 'bob@example.com',
        phone: '9123456789',
        mobile: '9123456789',
        age: 30,
        gender: 'Male',
        bloodGroup: 'B+',
        donorDisplayId: 1234, // Alice's taken ID!
        optInNotifications: true,
        createdAt: 1100,
        updatedAt: 1100
      });
      await assertFails(batch.commit());
    });

    await test('Donor Bob CANNOT forge profile using Alice reserved donorDisplayId 1234', async () => {
      await assertFails(donorBob.collection('donors').doc('donor_bob').set({
        uid: 'donor_bob',
        fullName: 'Bob Brown',
        name: 'Bob Brown',
        email: 'bob@example.com',
        phone: '9123456789',
        mobile: '9123456789',
        age: 30,
        gender: 'Male',
        bloodGroup: 'B+',
        donorDisplayId: 1234,
        optInNotifications: true,
        createdAt: 1100,
        updatedAt: 1100
      }));
    });

    await test('Donor Bob can legitimately register using atomic WriteBatch with ID 5678', async () => {
      const batch = donorBob.batch();
      batch.set(donorBob.collection('donorDisplayIds').doc('5678'), {
        donorUid: 'donor_bob',
        assignedAt: 1200
      });
      batch.set(donorBob.collection('donors').doc('donor_bob'), {
        uid: 'donor_bob',
        fullName: 'Bob Brown',
        name: 'Bob Brown',
        email: 'bob@example.com',
        phone: '9123456789',
        mobile: '9123456789',
        age: 30,
        gender: 'Male',
        bloodGroup: 'B+',
        donorDisplayId: 5678,
        optInNotifications: true,
        createdAt: 1200,
        updatedAt: 1200
      });
      await assertSucceeds(batch.commit());
    });

    await test('Repeated reservations: Bob CANNOT reserve a second donorDisplayId (5679) via batch update', async () => {
      const batch = donorBob.batch();
      batch.set(donorBob.collection('donorDisplayIds').doc('5679'), {
        donorUid: 'donor_bob',
        assignedAt: 1300
      });
      batch.update(donorBob.collection('donors').doc('donor_bob'), {
        updatedAt: 1300
      });
      await assertFails(batch.commit());
    });

    await test('Donor cannot create profile with unreserved random displayId 9999', async () => {
      await assertFails(donorAlice.collection('donors').doc('donor_charlie').set({
        uid: 'donor_charlie',
        fullName: 'Charlie Unreserved',
        name: 'Charlie Unreserved',
        email: 'charlie@example.com',
        phone: '9000000000',
        mobile: '9000000000',
        age: 28,
        gender: 'Male',
        bloodGroup: 'AB+',
        donorDisplayId: 9999, // Unreserved!
        optInNotifications: false,
        createdAt: 1300,
        updatedAt: 1300
      }));
    });

    await test('Donor profile creation rejects unexpected fields', async () => {
      const batch = donorAlice.batch();
      batch.set(donorAlice.collection('donorDisplayIds').doc('2222'), {
        donorUid: 'donor_extra',
        assignedAt: 1350
      });
      batch.set(donorAlice.collection('donors').doc('donor_extra'), {
        uid: 'donor_extra',
        fullName: 'Extra Field Donor',
        email: 'extra@example.com',
        phone: '9876543210',
        age: 26,
        gender: 'Female',
        bloodGroup: 'O+',
        donorDisplayId: 2222,
        optInNotifications: true,
        createdAt: 1350,
        updatedAt: 1350,
        maliciousKey: 'payload'
      });
      await assertFails(batch.commit());
    });

    await test('Donor profile creation rejects invalid age (< 16)', async () => {
      const batch = donorBob.batch();
      batch.set(donorBob.collection('donorDisplayIds').doc('7777'), {
        donorUid: 'donor_minor',
        assignedAt: 1400
      });
      batch.set(donorBob.collection('donors').doc('donor_minor'), {
        uid: 'donor_minor',
        fullName: 'Minor User',
        email: 'minor@example.com',
        phone: '9876543210',
        age: 14, // Invalid!
        gender: 'Male',
        bloodGroup: 'O+',
        donorDisplayId: 7777,
        optInNotifications: false,
        createdAt: 1400,
        updatedAt: 1400
      });
      await assertFails(batch.commit());
    });

    await test('Donor cannot modify immutable donorDisplayId, uid, or createdAt', async () => {
      await assertFails(donorAlice.collection('donors').doc('donor_alice').update({
        donorDisplayId: 5678
      }));
    });

    await test('Donor cannot self-assign screeningStatus on update', async () => {
      await assertFails(donorAlice.collection('donors').doc('donor_alice').update({
        screeningStatus: 'CLEARED'
      }));
    });

    await test('Staff can update donor screeningStatus to CLEARED', async () => {
      await assertSucceeds(staffSmith.collection('donors').doc('donor_alice').update({
        screeningStatus: 'CLEARED',
        updatedAt: 2500
      }));
    });

    await test('Staff cannot alter donor phone or email via screeningStatus update', async () => {
      await assertFails(staffSmith.collection('donors').doc('donor_alice').update({
        screeningStatus: 'CLEARED',
        phone: '0000000000'
      }));
    });

    // -------------------------------------------------------------------------
    // CATEGORY 3: Blood Donation Camps (/camps/{campId})
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 3: Blood Donation Camps (/camps/{campId}) ---');

    const validCamp = {
      campId: 'camp_101',
      campName: 'Central Blood Drive 2026',
      name: 'Central Blood Drive 2026',
      location: 'City Auditorium',
      date: '2026-11-15',
      timePeriod: '09:00 AM - 05:00 PM',
      status: 'PUBLISHED',
      staffUid: 'staff_smith',
      organizerStaffUid: 'staff_smith',
      createdAt: 2000
    };

    await test('Staff can create valid PUBLISHED camp with ownership matching auth.uid', async () => {
      await assertSucceeds(staffSmith.collection('camps').doc('camp_101').set(validCamp));
    });

    await test('Staff cannot create camp with unexpected fields', async () => {
      await assertFails(staffSmith.collection('camps').doc('camp_102').set({
        ...validCamp,
        campId: 'camp_102',
        extraField: 'not_allowed'
      }));
    });

    await test('Staff cannot create camp with invalid status (e.g. UNAPPROVED)', async () => {
      await assertFails(staffSmith.collection('camps').doc('camp_103').set({
        ...validCamp,
        campId: 'camp_103',
        status: 'UNAPPROVED'
      }));
    });

    await test('Staff cannot forge camp ownership with someone else UID', async () => {
      await assertFails(staffSmith.collection('camps').doc('camp_104').set({
        ...validCamp,
        campId: 'camp_104',
        staffUid: 'staff_jones',
        organizerStaffUid: 'staff_jones'
      }));
    });

    await test('Donor cannot create a camp', async () => {
      await assertFails(donorAlice.collection('camps').doc('camp_105').set({
        ...validCamp,
        campId: 'camp_105',
        staffUid: 'donor_alice'
      }));
    });

    await test('Camp owner staff can update camp location, timePeriod, status, updatedAt', async () => {
      await assertSucceeds(staffSmith.collection('camps').doc('camp_101').update({
        location: 'City Community Center Room B',
        timePeriod: '10:00 AM - 04:00 PM',
        status: 'PUBLISHED',
        updatedAt: 2500
      }));
    });

    await test('Camp owner staff cannot alter immutable campId, createdAt, or staffUid', async () => {
      await assertFails(staffSmith.collection('camps').doc('camp_101').update({
        staffUid: 'staff_jones',
        createdAt: 9999
      }));
    });

    await test('Other staff (not owner) cannot modify another staff camp', async () => {
      await assertFails(staffJones.collection('camps').doc('camp_101').update({
        location: 'Hijacked Location'
      }));
    });

    await test('Staff can create a DRAFT camp', async () => {
      await assertSucceeds(staffSmith.collection('camps').doc('camp_draft').set({
        ...validCamp,
        campId: 'camp_draft',
        status: 'DRAFT'
      }));
    });

    await test('Donor can read PUBLISHED camp', async () => {
      await assertSucceeds(donorAlice.collection('camps').doc('camp_101').get());
    });

    await test('Donor CANNOT read DRAFT camp (Private to staff)', async () => {
      await assertFails(donorAlice.collection('camps').doc('camp_draft').get());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 4: Camp Registrations (/campRegistrations/{regId})
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 4: Camp Registrations (/campRegistrations/{regId}) ---');

    const regId = 'camp_101_donor_alice';
    const validRegistration = {
      registrationId: regId,
      campId: 'camp_101',
      campTitle: 'Central Blood Drive 2026',
      donorUid: 'donor_alice',
      donorDisplayId: 1234,
      donorName: 'Alice Smith',
      bloodGroup: 'O+',
      status: 'REGISTERED',
      registeredAt: 3000
    };

    await test('Donor can register for an existing PUBLISHED camp', async () => {
      await assertSucceeds(donorAlice.collection('campRegistrations').doc(regId).set(validRegistration));
    });

    await test('Donor CANNOT register for a nonexistent camp', async () => {
      const nonExistentRegId = 'camp_ghost_donor_alice';
      await assertFails(donorAlice.collection('campRegistrations').doc(nonExistentRegId).set({
        ...validRegistration,
        registrationId: nonExistentRegId,
        campId: 'camp_ghost'
      }));
    });

    await test('Donor CANNOT register for a DRAFT (unpublished) camp', async () => {
      const draftRegId = 'camp_draft_donor_alice';
      await assertFails(donorAlice.collection('campRegistrations').doc(draftRegId).set({
        ...validRegistration,
        registrationId: draftRegId,
        campId: 'camp_draft'
      }));
    });

    await test('Donor cannot register with mismatched registration ID (must be {campId}_{uid})', async () => {
      await assertFails(donorAlice.collection('campRegistrations').doc('random_id').set({
        ...validRegistration,
        registrationId: 'random_id'
      }));
    });

    await test('Donor cannot register with unexpected fields', async () => {
      const regIdBob = 'camp_101_donor_bob';
      await assertFails(donorBob.collection('campRegistrations').doc(regIdBob).set({
        ...validRegistration,
        registrationId: regIdBob,
        donorUid: 'donor_bob',
        donorDisplayId: 5678,
        donorName: 'Bob Brown',
        unexpectedField: 'hack'
      }));
    });

    await test('Donor cannot self-assign status ATTENDED during registration', async () => {
      const regIdBob = 'camp_101_donor_bob';
      await assertFails(donorBob.collection('campRegistrations').doc(regIdBob).set({
        ...validRegistration,
        registrationId: regIdBob,
        donorUid: 'donor_bob',
        donorDisplayId: 5678,
        donorName: 'Bob Brown',
        status: 'ATTENDED' // Forged!
      }));
    });

    await test('Donor cannot self-mark attendance (status: ATTENDED) on update', async () => {
      await assertFails(donorAlice.collection('campRegistrations').doc(regId).update({
        status: 'ATTENDED'
      }));
    });

    await test('Donor cannot modify immutable registration fields (campId, donorDisplayId)', async () => {
      await assertFails(donorAlice.collection('campRegistrations').doc(regId).update({
        donorDisplayId: 9999
      }));
    });

    await test('Donor CAN legitimately cancel their own registration (status: CANCELLED)', async () => {
      await assertSucceeds(donorAlice.collection('campRegistrations').doc(regId).update({
        status: 'CANCELLED',
        updatedAt: 3200
      }));
    });

    await test('Staff CAN update registration attendance status (e.g. ATTENDED)', async () => {
      await assertSucceeds(staffSmith.collection('campRegistrations').doc(regId).update({
        status: 'ATTENDED',
        updatedAt: 3500
      }));
    });

    await test('Staff cannot alter donorName, donorDisplayId, or campId on registration update', async () => {
      await assertFails(staffSmith.collection('campRegistrations').doc(regId).update({
        donorName: 'Altered Name',
        donorDisplayId: 9999
      }));
    });

    await test('Donor Bob CANNOT read Alice camp registration (Private data isolation)', async () => {
      await assertFails(donorBob.collection('campRegistrations').doc(regId).get());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 5: /donationRecords/{recordId} Schema, Consistency & Staff Auth
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 5: Donation Records & Donor Profile Correspondence ---');

    const validRecord = {
      recordId: 'DONATION_donor_alice_2026-10-20',
      donorUid: 'donor_alice',
      donorDisplayId: 1234,
      donorName: 'Alice Smith',
      campId: 'camp_101',
      campName: 'Central Blood Drive 2026',
      staffUid: 'staff_smith',
      donationDate: '2026-10-20',
      bloodVolume: '450',
      volumeUnit: 'ml',
      hemoglobin: '13.8',
      bloodGroup: 'O+',
      status: 'COMPLETED',
      timestamp: 4000
    };

    await test('Staff can record a valid donation record matching verified donor profile', async () => {
      await assertSucceeds(staffSmith.collection('donationRecords').doc(validRecord.recordId).set(validRecord));
    });

    await test('Donation record rejects mismatched donorUid and donorDisplayId (Bob ID with Alice UID)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_mismatch').set({
        ...validRecord,
        recordId: 'rec_mismatch',
        donorUid: 'donor_alice',
        donorDisplayId: 5678 // Bob's displayId, not Alice's!
      }));
    });

    await test('Donation record rejects non-existent donorUid (Ghost donor profile)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_ghost').set({
        ...validRecord,
        recordId: 'rec_ghost',
        donorUid: 'ghost_donor_999',
        donorDisplayId: 1234
      }));
    });

    await test('Donation record rejects out-of-range bloodVolume (e.g. 150 ml)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_low_vol').set({
        ...validRecord,
        recordId: 'rec_low_vol',
        bloodVolume: '150'
      }));
    });

    await test('Donation record rejects out-of-range bloodVolume (e.g. 750 ml)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_high_vol').set({
        ...validRecord,
        recordId: 'rec_high_vol',
        bloodVolume: '750'
      }));
    });

    await test('Donation record rejects invalid volumeUnit (e.g. liters)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_unit').set({
        ...validRecord,
        recordId: 'rec_unit',
        volumeUnit: 'liters'
      }));
    });

    await test('Donation record rejects out-of-range hemoglobin (< 7.0 g/dL)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_low_hb').set({
        ...validRecord,
        recordId: 'rec_low_hb',
        hemoglobin: '5.2'
      }));
    });

    await test('Donation record rejects out-of-range hemoglobin (> 20.0 g/dL)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_high_hb').set({
        ...validRecord,
        recordId: 'rec_high_hb',
        hemoglobin: '24.5'
      }));
    });

    await test('Donation record rejects non-matching staffUid (staff impersonation)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_fake_staff').set({
        ...validRecord,
        recordId: 'rec_fake_staff',
        staffUid: 'staff_jones'
      }));
    });

    await test('Donation record rejects unexpected fields', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc('rec_extra').set({
        ...validRecord,
        recordId: 'rec_extra',
        fakeField: 'attack'
      }));
    });

    await test('Staff can update mutable fields on donation record (e.g. volume correction)', async () => {
      await assertSucceeds(staffSmith.collection('donationRecords').doc(validRecord.recordId).update({
        bloodVolume: '500',
        hemoglobin: '14.2',
        updatedAt: 4500
      }));
    });

    await test('Staff cannot update immutable record fields (recordId, donorUid, staffUid, timestamp, date)', async () => {
      await assertFails(staffSmith.collection('donationRecords').doc(validRecord.recordId).update({
        donorUid: 'donor_bob',
        donationDate: '2026-01-01'
      }));
    });

    await test('Donor Alice can read her own donation record', async () => {
      await assertSucceeds(donorAlice.collection('donationRecords').doc(validRecord.recordId).get());
    });

    await test('Donor Bob CANNOT read Alice donation record (Strict donor isolation)', async () => {
      await assertFails(donorBob.collection('donationRecords').doc(validRecord.recordId).get());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 6: Blood Requests & Privacy (/bloodRequests/{reqId})
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 6: Blood Requests & Privacy (/bloodRequests/{reqId}) ---');

    const validBloodReq = {
      requestId: 'REQ_1001',
      title: 'Urgent O+ Blood Needed',
      bloodGroup: 'O+',
      hospital: 'Metro General ICU',
      contactPhone: '+91 9876543210',
      urgency: 'CRITICAL',
      status: 'ONGOING',
      staffUid: 'staff_smith',
      createdAt: 5000
    };

    await test('Staff can publish a new ONGOING blood request', async () => {
      await assertSucceeds(staffSmith.collection('bloodRequests').doc(validBloodReq.requestId).set(validBloodReq));
    });

    await test('Staff cannot create request starting with status COMPLETED or CANCELLED', async () => {
      await assertFails(staffSmith.collection('bloodRequests').doc('REQ_BAD_START').set({
        ...validBloodReq,
        requestId: 'REQ_BAD_START',
        status: 'COMPLETED'
      }));
    });

    await test('Blood request rejects unexpected fields', async () => {
      await assertFails(staffSmith.collection('bloodRequests').doc('REQ_EXTRA').set({
        ...validBloodReq,
        requestId: 'REQ_EXTRA',
        unwantedField: 'bad'
      }));
    });

    await test('Staff can legitimately transition request from ONGOING to COMPLETED', async () => {
      await assertSucceeds(staffSmith.collection('bloodRequests').doc(validBloodReq.requestId).update({
        status: 'COMPLETED',
        updatedAt: 5500
      }));
    });

    await test('Staff CANNOT illegally reopen a COMPLETED request back to ONGOING (Invalid Transition)', async () => {
      await assertFails(staffSmith.collection('bloodRequests').doc(validBloodReq.requestId).update({
        status: 'ONGOING'
      }));
    });

    await test('Staff cannot alter immutable requestId, staffUid, createdAt, or bloodGroup on update', async () => {
      await assertFails(staffSmith.collection('bloodRequests').doc(validBloodReq.requestId).update({
        staffUid: 'staff_jones',
        bloodGroup: 'A-'
      }));
    });

    await test('Donor Alice can read COMPLETED blood request', async () => {
      await assertSucceeds(donorAlice.collection('bloodRequests').doc(validBloodReq.requestId).get());
    });

    await test('Staff can create CANCELLED blood request', async () => {
      await assertSucceeds(staffSmith.collection('bloodRequests').doc('REQ_CANCELLED').set({
        ...validBloodReq,
        requestId: 'REQ_CANCELLED',
        status: 'ONGOING'
      }));
      await assertSucceeds(staffSmith.collection('bloodRequests').doc('REQ_CANCELLED').update({
        status: 'CANCELLED'
      }));
    });

    await test('Donor CANNOT read CANCELLED blood request (Shielding private contact phone)', async () => {
      await assertFails(donorAlice.collection('bloodRequests').doc('REQ_CANCELLED').get());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 7: Request Responses (/requestResponses/{respId})
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 7: Request Responses (/requestResponses/{respId}) ---');

    const validResponse = {
      responseId: 'RESP_001',
      requestId: 'REQ_1001',
      donorUid: 'donor_alice',
      donorName: 'Alice Smith',
      bloodGroup: 'O+',
      status: 'OFFERED',
      message: 'Available to donate this afternoon',
      respondedAt: 6000
    };

    await test('Donor can submit an OFFERED response to an emergency request', async () => {
      await assertSucceeds(donorAlice.collection('requestResponses').doc('RESP_001').set(validResponse));
    });

    await test('Donor cannot inject staff-controlled keys (staffNotes, decidedAt) on create', async () => {
      await assertFails(donorAlice.collection('requestResponses').doc('RESP_002').set({
        ...validResponse,
        responseId: 'RESP_002',
        staffNotes: 'Self approved',
        decidedAt: 6100
      }));
    });

    await test('Donor can update their own response message or updatedAt', async () => {
      await assertSucceeds(donorAlice.collection('requestResponses').doc('RESP_001').update({
        message: 'Available until 6 PM today',
        updatedAt: 6200
      }));
    });

    await test('Donor CANNOT approve their own response (status: ACCEPTED) (Privilege Escalation)', async () => {
      await assertFails(donorAlice.collection('requestResponses').doc('RESP_001').update({
        status: 'ACCEPTED'
      }));
    });

    await test('Donor cannot update immutable fields (requestId, donorUid, respondedAt)', async () => {
      await assertFails(donorAlice.collection('requestResponses').doc('RESP_001').update({
        requestId: 'REQ_DIFFERENT',
        donorUid: 'donor_bob'
      }));
    });

    await test('Staff CAN update status to ACCEPTED with staffNotes and decidedByStaffUid', async () => {
      await assertSucceeds(staffSmith.collection('requestResponses').doc('RESP_001').update({
        status: 'ACCEPTED',
        staffNotes: 'Approved by Dr. Smith. Blood donation scheduled.',
        decidedByStaffUid: 'staff_smith',
        decidedAt: 6500,
        updatedAt: 6500
      }));
    });

    await test('Staff cannot alter immutable donorUid or requestId on response', async () => {
      await assertFails(staffSmith.collection('requestResponses').doc('RESP_001').update({
        donorUid: 'donor_bob'
      }));
    });

    await test('Donor Bob CANNOT read Alice request response (Strict private response access)', async () => {
      await assertFails(donorBob.collection('requestResponses').doc('RESP_001').get());
    });

    // -------------------------------------------------------------------------
    // CATEGORY 8: Global Unauthenticated Access Denial
    // -------------------------------------------------------------------------
    console.log('\n--- CATEGORY 8: Global Unauthenticated Access Denial ---');

    await test('Unauthenticated user cannot read staff profiles', async () => {
      await assertFails(unauthed.collection('staffs').doc('staff_smith').get());
    });

    await test('Unauthenticated user cannot read donor profiles', async () => {
      await assertFails(unauthed.collection('donors').doc('donor_alice').get());
    });

    await test('Unauthenticated user cannot read camps', async () => {
      await assertFails(unauthed.collection('camps').doc('camp_101').get());
    });

    await test('Unauthenticated user cannot read camp registrations', async () => {
      await assertFails(unauthed.collection('campRegistrations').doc(regId).get());
    });

    await test('Unauthenticated user cannot read donation records', async () => {
      await assertFails(unauthed.collection('donationRecords').doc(validRecord.recordId).get());
    });

    await test('Unauthenticated user cannot read blood requests', async () => {
      await assertFails(unauthed.collection('bloodRequests').doc(validBloodReq.requestId).get());
    });

    await test('Unauthenticated user cannot read request responses', async () => {
      await assertFails(unauthed.collection('requestResponses').doc('RESP_001').get());
    });

    await test('Unauthenticated user cannot write to any collection', async () => {
      await assertFails(unauthed.collection('arbitrary').doc('any').set({ a: 1 }));
    });

  } finally {
    if (testEnv) {
      await testEnv.cleanup();
    }
  }

  console.log(`\n======================================================================`);
  console.log(`FINAL RESULTS: ${passed} PASSED, ${failed} FAILED`);
  console.log(`======================================================================\n`);
  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
