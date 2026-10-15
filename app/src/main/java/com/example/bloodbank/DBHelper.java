package com.example.bloodbank;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "BloodBank.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_DONORS = "donors";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_DONORS + " (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "age INTEGER, " +
                "bloodGroup TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DONORS);
        onCreate(db);
    }

    public boolean insertDonor(int id, String name, int age, String bloodGroup) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("id", id);
        contentValues.put("name", name);
        contentValues.put("age", age);
        contentValues.put("bloodGroup", bloodGroup);
        long result = db.insert(TABLE_DONORS, null, contentValues);
        return result != -1;
    }

    public Cursor getAllDonors() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DONORS, null);
    }

    public boolean updateDonor(int id, String name, int age, String bloodGroup) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", name);
        contentValues.put("age", age);
        contentValues.put("bloodGroup", bloodGroup);
        int result = db.update(TABLE_DONORS, contentValues, "id = ?", new String[]{String.valueOf(id)});
        return result > 0;
    }

    public boolean deleteDonor(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_DONORS, "id = ?", new String[]{String.valueOf(id)});
        return result > 0;
    }
}