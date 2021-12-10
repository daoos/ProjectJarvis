package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class Devices extends AppCompatActivity {

    boolean lampBoolean;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MyUserPrefs", Context.MODE_PRIVATE);
        Button lampBtn = findViewById(R.id.lampBtn);

        //Updates the text field to show the current status
        TextView lampStatus = findViewById(R.id.lampStatus);
        lampStatus.setText(Boolean.valueOf(lampBoolean).toString());
        System.out.println(lampBoolean);

        lampBtn.setOnClickListener(v -> {
            System.out.println("Lamp click!");
            lampBoolean = !lampBoolean;
            System.out.println(lampBoolean);
            lampStatus.setText(Boolean.valueOf(lampBoolean).toString());

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("lampBoolean", lampBoolean);
            editor.apply();

            if (lampBoolean = true) {
                Toast.makeText(Devices.this, "Lamp turned on", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(Devices.this, "Lamp turned off", Toast.LENGTH_LONG).show();
            }
        });
    }
}