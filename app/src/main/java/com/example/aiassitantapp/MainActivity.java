package com.example.aiassitantapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.app.DatePickerDialog;
import android.provider.CalendarContract;
import android.widget.DatePicker;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    private String selectedDate = "";
    EditText mtlTxtResult;
    private static final int CREATE_FILE_REQUEST_CODE = 1001;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1002;
    private boolean isListening = false; // Variable to track "Listening" state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mtlTxtResult = findViewById(R.id.mtlTxtResult);
    }

    // Clear Result
    public void clearResult(View view) {
        mtlTxtResult.setText("");
    }

    // Export Result
    public void exportResult(View view) {
        String data = mtlTxtResult.getText().toString();
        if (!data.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "exported_data.txt");
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
        }
    }

    // Speech to text
    public void toggleListening(View view) {
        if (!isListening) { // If not listening
            isListening = true;
            promptSpeechInput();
        } else { // If listening
            isListening = false;
        }
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...");

        // Check if speech recognition is supported on device
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } else {
            Toast.makeText(this, "Speech recognition is not supported on your device", Toast.LENGTH_SHORT).show();
        }
    }

    // Add Event to SQLite
    public void addEvent(View view) {
        String data = mtlTxtResult.getText().toString().trim();
        if (data.isEmpty()) {
            Toast.makeText(this, "No data to process", Toast.LENGTH_SHORT).show();
            return;
        }

        String description = "";
        String eventDate = "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        try {
            String[] parts = data.split(", ");
            for (String part : parts) {
                if (part.startsWith("Event Description:")) {
                    description = part.replace("Event Description:", "").trim();
                } else if (part.startsWith("Date:")) {
                    eventDate = part.replace("Date:", "").trim();
                }
            }

            if (description.isEmpty() || eventDate.isEmpty()) {
                Toast.makeText(this, "Invalid data format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Insert the event into SQLite database
            dbHelper.addEvent(description, "Auto-added event from extracted data", eventDate);
            Toast.makeText(this, "Event added to database", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Invalid data format", Toast.LENGTH_SHORT).show();
        }
    }

    // View all stored events
    public void viewEvents(View view) {
        Cursor cursor = (Cursor) dbHelper.getAllEvents();
        if (cursor != null && cursor.moveToFirst()) {
            StringBuilder eventList = new StringBuilder();
            do {
                @SuppressLint("Range") String title = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_TITLE));
                @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_DATE));
                eventList.append("Title: ").append(title).append("\nDate: ").append(date).append("\n\n");
            } while (cursor.moveToNext());

            mtlTxtResult.setText(eventList.toString());
        } else {
            Toast.makeText(this, "No events found", Toast.LENGTH_SHORT).show();
        }
    }

    // Calendar function to select date
    public void openCalendar(View view) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                // Format the selected date as a string
                selectedDate = year + "-" + (monthOfYear + 1) + "-" + dayOfMonth;
                mtlTxtResult.setText(selectedDate);
            }
        }, year, month, day);

        datePickerDialog.show();
    }

    // Calendar function to store an event
    public void extractDataAndAddToCalendar(View view) {
        String data = mtlTxtResult.getText().toString().trim();
        if (data.isEmpty()) {
            Toast.makeText(this, "No data to process", Toast.LENGTH_SHORT).show();
            return;
        }

        // Example: Extracting data and date from the input text
        String description = ""; // Default description
        String eventDate = "";   // Default date string
        SimpleDateFormat sdf = new SimpleDateFormat("dd/mm/yyyy"); // Adjust format if needed

        try {
            String[] parts = data.split(", ");
            for (String part : parts) {
                if (part.startsWith("Event Description:")) {
                    description = part.replace("Event Description:", "").trim();
                } else if (part.startsWith("Date:")) {
                    eventDate = part.replace("Date:", "").trim();
                }
            }

            if (description.isEmpty() || eventDate.isEmpty()) {
                Toast.makeText(this, "Invalid data format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate the date
            sdf.setLenient(false); // Strict date parsing
            Calendar date = Calendar.getInstance();
            date.setTime(sdf.parse(eventDate));

            // Add event to the calendar
            addEventToCalendarWithDetails(description, date);

        } catch (ParseException e) {
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void addEventToCalendarWithDetails(String description, Calendar date) {
        // Store event in SQLite database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        String title = description;
        String eventDate = date.getTime().toString(); // Convert the date to string format
        dbHelper.addEvent(title, "Auto-added event from extracted data", eventDate);

        // Check if a calendar app is available
        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, description);
        intent.putExtra(CalendarContract.Events.DESCRIPTION, "Auto-added event from extracted data");

        Calendar startTime = (Calendar) date.clone();
        startTime.set(Calendar.HOUR_OF_DAY, 9);

        Calendar endTime = (Calendar) date.clone();
        endTime.set(Calendar.HOUR_OF_DAY, 10);

        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime.getTimeInMillis());
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime.getTimeInMillis());

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fallback to SQLite storage
            Toast.makeText(this, "No calendar app available. Event stored locally.", Toast.LENGTH_LONG).show();
            //showEventsFromDatabase(); // Display stored events
        }
    }

    // Extract date from the text using regex
    private String extractDate(String text) {
        // Pattern to match dates in format dd/MM/yyyy
        Pattern pattern = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1); // Return the first matched date
        }
        return null; // No valid date found
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Export Data
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        outputStream.write(mtlTxtResult.getText().toString().getBytes());
                        outputStream.close();
                        Toast.makeText(this, "Data exported successfully", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to export data", Toast.LENGTH_SHORT).show();
                }
            }
        }

        // Speak to text
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result.size() > 0) {
                    mtlTxtResult.setText(result.get(0)); // Set the first result (most likely option)
                }
            }
        }
    }
}
