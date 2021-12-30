package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

public class Devices extends AppCompatActivity {

    private final Link link = new Link();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        Button turnOnBtn2 = findViewById(R.id.turnOnBtn2);
        turnOnBtn2.setOnClickListener(v -> {
            link.publish("project-jarvis/floor-lamp", "device/turnOn");
        });

        Button turnOffBtn = findViewById(R.id.turnOffBtn2);
        turnOffBtn.setOnClickListener(v -> {
            link.publish("project-jarvis/floor-lamp", "device/turnOff");
        });
    }
}