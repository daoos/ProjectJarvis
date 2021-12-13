package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class Devices extends AppCompatActivity {

    //TextView lampStatusss = findViewById(R.id.lampStatus);
    Switch lightToggle = null;
    public MainActivity mainObject = new MainActivity();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        lightToggle = (Switch) findViewById(R.id.switch1);

        lightToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    //Async for values
                    new AsyncTask<Integer, Void, Void>(){
                        @Override
                        protected Void doInBackground(Integer... params) {
                            mainObject.run("python turnondevice.py");
                            return null;
                        }
                    }.execute(1);
                } else {
                    //turn off lamp
                    //Async for values
                    new AsyncTask<Integer, Void, Void>(){
                        @Override
                        protected Void doInBackground(Integer... params) {
                            mainObject.run("python turnoffdevice.py");
                            return null;
                        }
                    }.execute(1);
                }
            }
        });

        /**
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
        */
    }
}