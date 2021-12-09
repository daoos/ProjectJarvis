package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.Bundle;

import java.util.HashMap;

public class Devices extends AppCompatActivity {

    private SwitchCompat lampSwitch;
    private MainActivity mainActivity = new MainActivity();
    private HashMap<String, Boolean> toggles = mainActivity.getToggles();
    boolean lampSwitchToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        lampSwitch = findViewById(R.id.lampSwitch);

        lampSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (toggles.get("lampSwitch") != null) {
                lampSwitchToggle = mainActivity.getToggles().get("lampSwitch");
            }
        });
    }
}