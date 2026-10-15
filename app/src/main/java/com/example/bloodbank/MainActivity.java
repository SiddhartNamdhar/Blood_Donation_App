package com.example.bloodbank;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView imgCampBanner;
    private EditText etDonorId, etDonorName, etDonorAge;
    private Spinner spBloodGroup;
    private ListView lvDonors;

    private DBHelper dbHelper;

    private final ArrayList<Integer> idList = new ArrayList<>();
    private final ArrayList<String> nameList = new ArrayList<>();
    private final ArrayList<Integer> ageList = new ArrayList<>();
    private final ArrayList<String> bloodGroupList = new ArrayList<>();

    private final String[] bloodGroups = {"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register ActivityResultLaunchers for Gallery and Camera
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        imgCampBanner.setImageURI(uri);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        imgCampBanner.setImageBitmap(bitmap);
                    }
                }
        );

        // Bind Views
        imgCampBanner = findViewById(R.id.imgCampBanner);
        etDonorId = findViewById(R.id.etDonorId);
        etDonorName = findViewById(R.id.etDonorName);
        etDonorAge = findViewById(R.id.etDonorAge);
        spBloodGroup = findViewById(R.id.spBloodGroup);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnEdit = findViewById(R.id.btnEdit);
        Button btnDelete = findViewById(R.id.btnDelete);
        lvDonors = findViewById(R.id.lvDonors);

        // Initialize DBHelper
        dbHelper = new DBHelper(this);

        // Setup Blood Group Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBloodGroup.setAdapter(spinnerAdapter);

        // Camp Banner Popup Menu
        imgCampBanner.setOnClickListener(this::showBannerPopupMenu);

        // Load Donors
        refreshList();

        // Tapping a donor fills the form
        lvDonors.setOnItemClickListener((parent, view, position, id) -> {
            if (position < idList.size()) {
                etDonorId.setText(String.valueOf(idList.get(position)));
                etDonorName.setText(nameList.get(position));
                etDonorAge.setText(String.valueOf(ageList.get(position)));
                String bg = bloodGroupList.get(position);
                for (int i = 0; i < bloodGroups.length; i++) {
                    if (bloodGroups[i].equalsIgnoreCase(bg)) {
                        spBloodGroup.setSelection(i);
                        break;
                    }
                }
            }
        });

        // Register ListView for Context Menu
        registerForContextMenu(lvDonors);

        // Add Button
        btnAdd.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            String name = etDonorName.getText().toString().trim();
            String ageStr = etDonorAge.getText().toString().trim();
            String bloodGroup = spBloodGroup.getSelectedItem().toString();

            if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int donorId = Integer.parseInt(idStr);
            int age = Integer.parseInt(ageStr);

            boolean success = dbHelper.insertDonor(donorId, name, age, bloodGroup);
            if (success) {
                Toast.makeText(MainActivity.this, "Donor added successfully", Toast.LENGTH_SHORT).show();
                refreshList();
                clearForm();
            } else {
                Toast.makeText(MainActivity.this, "Error adding donor (ID may already exist)", Toast.LENGTH_SHORT).show();
            }
        });

        // Edit Button
        btnEdit.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            String name = etDonorName.getText().toString().trim();
            String ageStr = etDonorAge.getText().toString().trim();
            String bloodGroup = spBloodGroup.getSelectedItem().toString();

            if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int donorId = Integer.parseInt(idStr);
            int age = Integer.parseInt(ageStr);

            boolean success = dbHelper.updateDonor(donorId, name, age, bloodGroup);
            if (success) {
                Toast.makeText(MainActivity.this, "Donor updated successfully", Toast.LENGTH_SHORT).show();
                refreshList();
            } else {
                Toast.makeText(MainActivity.this, "Donor ID not found for update", Toast.LENGTH_SHORT).show();
            }
        });

        // Delete Button (asks confirmation in AlertDialog)
        btnDelete.setOnClickListener(v -> {
            String idStr = etDonorId.getText().toString().trim();
            if (idStr.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter or select a Donor ID to delete", Toast.LENGTH_SHORT).show();
                return;
            }
            int donorId = Integer.parseInt(idStr);
            showDeleteConfirmationDialog(donorId);
        });
    }

    private void refreshList() {
        idList.clear();
        nameList.clear();
        ageList.clear();
        bloodGroupList.clear();

        Cursor cursor = dbHelper.getAllDonors();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                idList.add(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                nameList.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                ageList.add(cursor.getInt(cursor.getColumnIndexOrThrow("age")));
                bloodGroupList.add(cursor.getString(cursor.getColumnIndexOrThrow("bloodGroup")));
            }
            cursor.close();
        }

        CustomAdapter customAdapter = new CustomAdapter(this, idList, nameList, ageList, bloodGroupList);
        lvDonors.setAdapter(customAdapter);
    }

    private void clearForm() {
        etDonorId.setText("");
        etDonorName.setText("");
        etDonorAge.setText("");
        spBloodGroup.setSelection(0);
    }

    private void showDeleteConfirmationDialog(int donorId) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete donor with ID " + donorId + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    boolean success = dbHelper.deleteDonor(donorId);
                    if (success) {
                        Toast.makeText(MainActivity.this, "Donor deleted successfully", Toast.LENGTH_SHORT).show();
                        refreshList();
                        clearForm();
                    } else {
                        Toast.makeText(MainActivity.this, "Donor ID not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showBannerPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Gallery");
        popup.getMenu().add("Camera");
        popup.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (title != null && "Gallery".contentEquals(title)) {
                galleryLauncher.launch("image/*");
                return true;
            } else if (title != null && "Camera".contentEquals(title)) {
                cameraLauncher.launch(null);
                return true;
            }
            return false;
        });
        popup.show();
    }

    // Context Menu for ListView items
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.lvDonors) {
            menu.setHeaderTitle("Donor Options");
            menu.add(0, 1, 0, "Edit");
            menu.add(0, 2, 0, "Delete");
            menu.add(0, 3, 0, "Details");
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) return super.onContextItemSelected(item);

        int position = info.position;
        if (position >= idList.size()) return super.onContextItemSelected(item);

        int donorId = idList.get(position);
        String name = nameList.get(position);
        int age = ageList.get(position);
        String bloodGroup = bloodGroupList.get(position);

        switch (item.getItemId()) {
            case 1: // Edit
                etDonorId.setText(String.valueOf(donorId));
                etDonorName.setText(name);
                etDonorAge.setText(String.valueOf(age));
                for (int i = 0; i < bloodGroups.length; i++) {
                    if (bloodGroups[i].equalsIgnoreCase(bloodGroup)) {
                        spBloodGroup.setSelection(i);
                        break;
                    }
                }
                return true;

            case 2: // Delete
                showDeleteConfirmationDialog(donorId);
                return true;

            case 3: // Details
                new AlertDialog.Builder(this)
                        .setTitle("Donor Details")
                        .setMessage("ID: " + donorId + "\nName: " + name + "\nAge: " + age + "\nBlood Group: " + bloodGroup)
                        .setPositiveButton("OK", null)
                        .show();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    // Options Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save_txt) {
            saveToTxt();
            return true;
        } else if (id == R.id.menu_read_txt) {
            readTxt();
            return true;
        } else if (id == R.id.menu_save_csv) {
            saveToCsv();
            return true;
        } else if (id == R.id.menu_read_csv) {
            readCsv();
            return true;
        } else if (id == R.id.menu_delete_files) {
            deleteFiles();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveToTxt() {
        String idStr = etDonorId.getText().toString().trim();
        String name = etDonorName.getText().toString().trim();
        String ageStr = etDonorAge.getText().toString().trim();
        String bloodGroup = spBloodGroup.getSelectedItem() != null ? spBloodGroup.getSelectedItem().toString() : "";

        if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
            Toast.makeText(this, "Please enter donor details in the form first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File file = new File(getFilesDir(), "donors.txt");
            FileWriter writer = new FileWriter(file, true);
            writer.append(idStr).append(", ").append(name).append(", ").append(ageStr).append(", ").append(bloodGroup).append("\n");
            writer.flush();
            writer.close();
            Toast.makeText(this, "Saved to donors.txt", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving donor data to TXT file", e);
            Toast.makeText(this, "Error saving to TXT", Toast.LENGTH_SHORT).show();
        }
    }

    private void readTxt() {
        File file = new File(getFilesDir(), "donors.txt");
        if (!file.exists()) {
            Toast.makeText(this, "donors.txt does not exist", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading donor data from TXT file", e);
            Toast.makeText(this, "Error reading TXT file", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = sb.length() > 0 ? sb.toString() : "File is empty";
        new AlertDialog.Builder(this)
                .setTitle("donors.txt Contents")
                .setMessage(content)
                .setPositiveButton("OK", null)
                .show();
    }

    private void saveToCsv() {
        String idStr = etDonorId.getText().toString().trim();
        String name = etDonorName.getText().toString().trim();
        String ageStr = etDonorAge.getText().toString().trim();
        String bloodGroup = spBloodGroup.getSelectedItem() != null ? spBloodGroup.getSelectedItem().toString() : "";

        if (idStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
            Toast.makeText(this, "Please enter donor details in the form first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File file = new File(getFilesDir(), "donors.csv");
            boolean isNew = !file.exists();
            FileWriter writer = new FileWriter(file, true);
            if (isNew) {
                writer.append("id,name,age,bloodGroup\n");
            }
            writer.append(idStr).append(",").append(name).append(",").append(ageStr).append(",").append(bloodGroup).append("\n");
            writer.flush();
            writer.close();
            Toast.makeText(this, "Saved to donors.csv", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving donor data to CSV file", e);
            Toast.makeText(this, "Error saving to CSV", Toast.LENGTH_SHORT).show();
        }
    }

    private void readCsv() {
        File file = new File(getFilesDir(), "donors.csv");
        if (!file.exists()) {
            Toast.makeText(this, "donors.csv does not exist", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder recordsBuilder = new StringBuilder();
        int oNegativeCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip CSV header
                    continue;
                }
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    String donorName = parts[1].trim();
                    String age = parts[2].trim();
                    String bloodGroup = parts[3].trim();

                    recordsBuilder.append("ID: ").append(id)
                            .append(", Name: ").append(donorName)
                            .append(", Age: ").append(age)
                            .append(", Blood Group: ").append(bloodGroup)
                            .append("\n");

                    if ("O-".equalsIgnoreCase(bloodGroup)) {
                        oNegativeCount++;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading donor data from CSV file", e);
            Toast.makeText(this, "Error reading CSV file", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = recordsBuilder.length() > 0 ? recordsBuilder.toString() : "No records found.";
        message += "\n-----------------------\nTotal O- Donors: " + oNegativeCount;

        new AlertDialog.Builder(this)
                .setTitle("Read CSV Results")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void deleteFiles() {
        File txtFile = new File(getFilesDir(), "donors.txt");
        File csvFile = new File(getFilesDir(), "donors.csv");

        boolean txtDeleted = false;
        boolean csvDeleted = false;

        if (txtFile.exists()) {
            txtDeleted = txtFile.delete();
        }
        if (csvFile.exists()) {
            csvDeleted = csvFile.delete();
        }

        Toast.makeText(this, "Files deleted (TXT: " + (txtDeleted ? "Deleted" : "Not found") + ", CSV: " + (csvDeleted ? "Deleted" : "Not found") + ")", Toast.LENGTH_SHORT).show();
    }
}