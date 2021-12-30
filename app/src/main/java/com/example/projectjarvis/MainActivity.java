package com.example.projectjarvis;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView voiceInput;
    private TextToSpeech textToSpeech;
    private Controller controller;
    private final Link link = new Link();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button turnOnBtn = findViewById(R.id.turnOnBtn);
        turnOnBtn.setOnClickListener(v -> {
            link.publish("project-jarvis/floor-lamp", "device/turnOn");
        });

        Button turnOffBtn = findViewById(R.id.turnOffBtn);
        turnOffBtn.setOnClickListener(v -> {
            link.publish("project-jarvis/floor-lamp", "device/turnOff");
        });

        ImageButton voiceBtn = findViewById(R.id.voiceBtn); //button for activating voice recognition
        voiceInput = findViewById(R.id.voiceInput); //textview for showing the voice input
        ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            //textToSpeech.setLanguage(Locale.US) TODO: Can't set locale on virtual machine
        }
        );
        controller = new Controller(textToSpeech, getApplicationContext());

        //MQTT Connect

        try {
            link.mqttConnect("project-jarvis/floor-lamp", getApplicationContext()); //TODO variabel
        } catch (MqttException e) {
            e.printStackTrace();
        }


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
                    controller.decodeInput(result);
                }
                break;
        }
    }


}