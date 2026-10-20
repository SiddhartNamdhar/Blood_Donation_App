package com.example.bloodbank;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "BloodBank.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_DONORS = "donors";
    public static final String TABLE_DONATION_RECORDS = "donation_records";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createDonorsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DONORS + " (" +
                "id INTEGER PRIMARY KEY, " +
                "donorUid TEXT, " +
                "name TEXT, " +
                "age INTEGER, " +
                "bloodGroup TEXT, " +
                "email TEXT, " +
                "mobile TEXT, " +
                "gender TEXT, " +
                "screeningStatus TEXT, " +
                "bloodDonated TEXT, " +
                "hemoglobin TEXT)";
        db.execSQL(createDonorsTable);

        String createRecordsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DONATION_RECORDS + " (" +
                "recordId TEXT PRIMARY KEY, " +
                "donorUid TEXT, " +
                "donorDisplayId INTEGER, " +
                "campId TEXT, " +
                "staffUid TEXT, " +
                "donationDate TEXT, " +
                "bloodVolume TEXT, " +
                "volumeUnit TEXT, " +
                "hemoglobin TEXT, " +
                "bloodGroup TEXT, " +
                "status TEXT, " +
                "timestamp INTEGER)";
        db.execSQL(createRecordsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            String createRecordsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DONATION_RECORDS + " (" +
                    "recordId TEXT PRIMARY KEY, " +
                    "donorUid TEXT, " +
                    "donorDisplayId INTEGER, " +
                    "campId TEXT, " +
                    "staffUid TEXT, " +
                    "donationDate TEXT, " +
                    "bloodVolume TEXT, " +
                    "volumeUnit TEXT, " +
                    "hemoglobin TEXT, " +
                    "bloodGroup TEXT, " +
                    "status TEXT, " +
                    "timestamp INTEGER)";
            db.execSQL(createRecordsTable);

            try {
                db.execSQL("ALTER TABLE " + TABLE_DONORS + " ADD COLUMN donorUid TEXT");
            } catch (Exception ignored) {}

            try {
                db.execSQL("ALTER TABLE " + TABLE_DONORS + " ADD COLUMN screeningStatus TEXT");
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean insertDonorFull(int id, String donorUid, String name, int age, String bloodGroup, String email, String mobile, String gender, String screeningStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("id", id);
        contentValues.put("donorUid", donorUid);
        contentValues.put("name", name);
        contentValues.put("age", age);
        contentValues.put("bloodGroup", bloodGroup);
        contentValues.put("email", email);
        contentValues.put("mobile", mobile);
        contentValues.put("gender", gender);
        contentValues.put("screeningStatus", screeningStatus);
        long result = db.insertWithOnConflict(TABLE_DONORS, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean insertDonationRecord(String recordId, String donorUid, int donorDisplayId, String campId, String staffUid, String donationDate, String bloodVolume, String volumeUnit, String hemoglobin, String bloodGroup, String status, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("recordId", recordId);
        contentValues.put("donorUid", donorUid);
        contentValues.put("donorDisplayId", donorDisplayId);
        contentValues.put("campId", campId);
        contentValues.put("staffUid", staffUid);
        contentValues.put("donationDate", donationDate);
        contentValues.put("bloodVolume", bloodVolume);
        contentValues.put("volumeUnit", volumeUnit);
        contentValues.put("hemoglobin", hemoglobin);
        contentValues.put("bloodGroup", bloodGroup);
        contentValues.put("status", status);
        contentValues.put("timestamp", timestamp);
        long result = db.insertWithOnConflict(TABLE_DONATION_RECORDS, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public Cursor getAllDonors() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DONORS, null);
    }

    public Cursor getDonorById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DONORS + " WHERE id = ?", new String[]{String.valueOf(id)});
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public Cursor getDonationRecordsByDonorUid(String donorUid) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DONATION_RECORDS + " WHERE donorUid = ? ORDER BY timestamp DESC", new String[]{donorUid});
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public Cursor getAllDonationRecords() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DONATION_RECORDS + " ORDER BY timestamp DESC", null);
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean updateDonationStats(int id, String bloodDonated, String hemoglobin) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("bloodDonated", bloodDonated);
        contentValues.put("hemoglobin", hemoglobin);
        int result = db.update(TABLE_DONORS, contentValues, "id = ?", new String[]{String.valueOf(id)});
        return result > 0;
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean updateScreeningStatus(int id, String screeningStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("screeningStatus", screeningStatus);
        int result = db.update(TABLE_DONORS, contentValues, "id = ?", new String[]{String.valueOf(id)});
        return result > 0;
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean deleteDonor(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_DONORS, "id = ?", new String[]{String.valueOf(id)});
        return result > 0;
    }
}