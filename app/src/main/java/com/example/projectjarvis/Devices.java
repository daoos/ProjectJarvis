package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class Devices extends AppCompatActivity {

    private Switch lightToggle;
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
                            System.out.println("Turning on device");
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
                            System.out.println("Turning off device");
                            return null;
                        }
                    }.execute(1);
                }
            }
        });
    }
}