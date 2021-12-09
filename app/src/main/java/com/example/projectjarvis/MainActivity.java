package com.example.projectjarvis;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;


public class MainActivity extends AppCompatActivity {

    //TODO: Seems like the design pattern to use a "sharedPreferences" instead, same usage?
    //Collection for keeping track of the state of our actuators
    public HashMap<String, Boolean> toggles = new HashMap<>();

    //Layout setup
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton micBtn = findViewById(R.id.voiceBtn);       //button for activating voice recog
        TextView voiceInput = findViewById(R.id.voiceInput);    //textview for showing the voice input
        ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        //all the actuator buttons, turned off if null
        //TODO: Check how to control their state through Telldus or Raspberry?
        if (toggles.get("lampSwitch") == null) {
            toggles.put("lampSwitch", false);
        }

        micBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                voiceInput.setText("Starting voice recognition!");
                System.out.println("Starting voice recognition!");

                //Start voice recognition - voiceListener Class?
                //Decode voice input
                //React to voice input
            }
        });

        devicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), Devices.class);
                startActivity(intent);

                System.out.println("Opening devices!");
            }
        });

    }

    //SSH-Kopplingen
    public void run(String command) { //TODO: Fixa till denna så den är mer "våran"?
        String hostname = "hostname"; //Raspberry IP
        String username = "username"; //see lab
        String password = "password"; //see lab
        try {
            StrictMode.ThreadPolicy policy = new
                    StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);

            Connection conn = new Connection(hostname); //init connection
            conn.connect(); //start connection to the hostname
            boolean isAuthenticated = conn.authenticateWithPassword(username,
                    password);
            if (!isAuthenticated)
                throw new IOException("Authentication failed.");
            Session session = conn.openSession();
            session.execCommand(command);
            InputStream stdout = new StreamGobbler(session.getStdout());
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout)); //reads text

            while (true) {
                String line = br.readLine(); // read line
                if (line == null)
                    break;
                System.out.println(line);
            }

            /* Show exit status, if available (otherwise "null") */
            System.out.println("ExitCode: " + session.getExitStatus());
            session.close(); // Close this session
            conn.close();

        } catch (IOException e) {
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }

    public HashMap<String, Boolean> getToggles() {
        return toggles;
    }

    public void setToggles(HashMap<String, Boolean> toggles) {
        this.toggles = toggles;
    }
}