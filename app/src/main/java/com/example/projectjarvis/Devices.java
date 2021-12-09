package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;

public class Devices extends AppCompatActivity {

    private SwitchCompat lampSwitch;
    private MainActivity mainActivity = new MainActivity();
    private HashMap<String, Boolean> toggles = mainActivity.getToggles();
    private boolean lampSwitchBoolean = false;
    private Button lampBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        lampSwitch = findViewById(R.id.lampSwitch);
        if (toggles.get("lampSwitch") != null) {
            lampSwitchBoolean = toggles.get("lampSwitch");
            System.out.println("NOT NULL!");
        } else {
            System.out.println("NULL");
        }
        lampSwitch.setChecked(lampSwitchBoolean);

        lampBtn = findViewById(R.id.lampBtn);

        //Updates the text field to show the current status
        TextView lampStatus = findViewById(R.id.lampStatus);
        lampStatus.setText(Boolean.valueOf(lampSwitchBoolean).toString());

        //TODO: Fix this! Won't show for some reason?
        lampSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            lampSwitch.toggle();
            lampSwitchBoolean = !lampSwitchBoolean;
            toggles.put("lampSwitch", lampSwitchBoolean);
            mainActivity.toggles = toggles;
            System.out.println(mainActivity.toggles);
            System.out.println(toggles);
            //mainActivity.setToggles(toggles);
            lampStatus.setText(Boolean.valueOf(lampSwitchBoolean).toString());
        });

        lampBtn.setOnClickListener(v -> {
            System.out.println("Lamp click!");
            lampSwitchBoolean = !lampSwitchBoolean;
            toggles.put("lampSwitch", lampSwitchBoolean);
            mainActivity.toggles = toggles;
            System.out.println(mainActivity.toggles);
            System.out.println(toggles);
            //mainActivity.setToggles(toggles);
            lampStatus.setText(Boolean.valueOf(lampSwitchBoolean).toString());
        });
    }
}