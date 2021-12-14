package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

public class Devices extends AppCompatActivity {

    private Switch lightToggle;
    private final Link linkObject = new Link();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        lightToggle = findViewById(R.id.switch1);

        lightToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    //linkObject.actuatorControl("on");
                } else {
                    //linkObject.actuatorControl("off");
                }
            }
        });
    }
}