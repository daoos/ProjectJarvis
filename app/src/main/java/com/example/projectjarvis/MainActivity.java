package com.example.projectjarvis;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

public class MainActivity extends AppCompatActivity {

    private static SharedPreferences prefs;
    private TextView voiceInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton voiceBtn = findViewById(R.id.voiceBtn);       //button for activating voice recognition
        voiceInput = findViewById(R.id.voiceInput);    //textview for showing the voice input
        ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        prefs = getSharedPreferences("MyUserPrefs", Context.MODE_PRIVATE);

        devicesBtn.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), Devices.class);
            startActivity(intent);

            System.out.println("Opening devices!");
        });

        voiceBtn.setOnClickListener(v -> {
            getSpeechInput(v.getRootView()); //activates voice recog when clicking
        });
    }

    public void getSpeechInput(View view) {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, 10);
        } else {
            Toast.makeText(this, "Your device does not support speech input", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 10:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    voiceInput.setText(result.get(0));
                    decodeInput(result);
                }
                break;
        }
    }

    private void decodeInput(ArrayList<String> result) {
        //TODO: skulle kunna skapa en samling med fraser som är okej? Ev göra det i en egen klass eller typ JSON?
        String resultString = result.toString().toLowerCase(); //TODO: Move this into onActivityResult ist? Snyggare för användaren
        if (resultString.contains("turn on the lamp")) {
            System.out.println("Turn on the lamp!");
        } else if (resultString.contains(("turn off the lamp"))) {
            System.out.println("Turn off the lamp!");
        } else {
            System.out.println("No valid input, try again!");
            //Lägg till en text-to-speech eller en ljudfil här
        }
    }

//    private void altDecode(ArrayList<String> result) {
//        String resultString = result.toString().toLowerCase();
//        ArrayList<String> commands = new ArrayList<>();
//        String turnOn = "turn on the lamp", turnOff = "turn off the lamp";
//        commands.add(turnOn);
//        commands.add(turnOff);
//
//        for (String command : commands) {
//            if (resultString.contains(command)) {
//                System.out.println(command);
//            }
//        }
//    }

//    private void activation(ArrayList<String> result) {
//        String resultString = result.toString().toLowerCase();
//        //TODO: detta är det mest resurseffektiva men funkar nog inte nu
//        switch (resultString) {
//            case "turn the lamp on":
//            case "turn on the lamp":
//            case "turn on lamp":
//                System.out.println("Turning on the lamp");
//            case "turn the lamp off":
//            case "turn off the lamp":
//            case "turn off lamp":
//                System.out.println("Turning off the lamp");
//            default:
//                System.out.println("No valid input, please try again");
//        }
//    }


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
}