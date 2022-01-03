package com.example.projectjarvis;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements
        RecognitionListener {

    //9225e610-2149-44b1-aee9-ca6eef3ce0c1

    private TextToSpeech textToSpeech;
    private static final String FLOOR_LAMP = "project-jarvis/floor-lamp";
    private MqttAndroidClient client;
    private static final String SERVER_URI = "tcp://test.mosquitto.org:1883";
    private static final String TAG = "MainActivity";

    //creates the ringtone / alarm
    private boolean ringtoneActive = false;
    private static final String ALARM_TOPIC_CREATE = "project-jarvis/alarm/create";
    private static final String ALARM_TOPIC_CONTROL = "project-jarvis/alarm/control";
    private static final String[] alarmTopics = {ALARM_TOPIC_CONTROL, ALARM_TOPIC_CREATE};
    private static final String SHOPPING_LIST_CREATE = "project-jarvis/shopping-list/create"; //TODO: ONÖDIG?
    private static final String SHOPPING_LIST_CONTROL = "project-jarvis/shopping-list/control";
    private static final String SHOPPING_LIST_READ = "project-jarvis/shopping-list/read";
    private final Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    private Ringtone r;

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView voiceInput;


    EditText cityInput, countryInput;
    TextView weatherResult;
    private final String url = "https://api.openweathermap.org/data/2.5/weather";
    private final String appid = "e53301e27efa0b66d05045d91b2742d3";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cityInput = findViewById(R.id.etCity);
        countryInput = findViewById(R.id.etCountry);
        weatherResult = findViewById(R.id.tvResult);

        //------Vosk
        voiceInput = findViewById(R.id.voiceInput); //textview for showing the voice input
        setUiState(STATE_START);

        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        //((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
        //------Vosk

        Button turnOnBtn = findViewById(R.id.turnOnBtn);
        turnOnBtn.setOnClickListener(v -> publish(FLOOR_LAMP, "device/turnOn"));

        Button turnOffBtn = findViewById(R.id.turnOffBtn);
        turnOffBtn.setOnClickListener(v -> publish(FLOOR_LAMP, "device/turnOff"));

        ImageButton voiceBtn = findViewById(R.id.voiceBtn); //button for activating voice recognition
        ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        r = RingtoneManager.getRingtone(getApplicationContext(), notification);

        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            //textToSpeech.setLanguage(Locale.US) TODO: Can't set locale on virtual machine
        }
        );

        //MQTT Connect

        try {
            mqttConnect(FLOOR_LAMP); //TODO variabel
        } catch (MqttException e) {
            e.printStackTrace();
        }


        devicesBtn.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), Devices.class);
            startActivity(intent);

            System.out.println("Opening devices!");
        });

        //TODO: Remove alarm button, change to voice recog
        //TODO: Decode voice input and send a name and n seconds to ALARM_TOPIC!
        //TODO: Message should be written: "Alarm name, length, repeatable? (bool)"
        Button alarmBtn = findViewById(R.id.alarm);
        alarmBtn.setOnClickListener(v -> {
            if (!ringtoneActive) {
//                Calendar date = Calendar.getInstance();
//                System.out.println("Current Date and Time : " + date.getTime());
//                long timeInSecs = date.getTimeInMillis();
//                Date afterAdding = new Date(timeInSecs + (10 * 1000)); //adds ten seconds
//                System.out.println("After adding 1 min : " + afterAdding);
//                String setTime = afterAdding.toString();
                String alarmName = "Wake up";
                String alarmLength = "10";
                String repeatable = "false";
                String message = alarmName + "," + alarmLength + "," + repeatable;
                publish(ALARM_TOPIC_CREATE, message);
                System.out.println("Publishing alarm for " + alarmName);

            } else {
                publish(ALARM_TOPIC_CONTROL, "stop");
                System.out.println("publishing stop");
            }
        });
    }

    // Old LINK
    public void mqttConnect(String topic) throws MqttException {
        String feedbackTopic = "project-jarvis/feedback";
        connect();
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    System.out.println("Reconnected to : " + serverURI); // Re-subscribe as we lost it due to new session
                } else {
                    System.out.println("Connected to: " + serverURI);
                }
                subscribe(topic); //TODO - Byt ut?
                subscribe(feedbackTopic);
                for (String topic : alarmTopics) {
                    subscribe(topic);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String newMessage = new String(message.getPayload());
                System.out.println("Incoming message: " + topic + " " + newMessage);
                String[] separatedMessage = newMessage.split(",");

                if (topic.equals(ALARM_TOPIC_CONTROL)) {
                    System.out.println("alarmtopic!");
                    try {
                        if (newMessage.contains("play")) {
                            System.out.println("Playing alarm");
                            r.play();
                            ringtoneActive = true;
                            feedback("Alarm: " + separatedMessage[1]);
                        } else if (newMessage.contains("stop")) {
                            System.out.println("Turning off alarm");
                            r.stop();
                            ringtoneActive = false;
                        }
                    } catch (Exception e) {
                        System.out.println("Alarm error");
                        e.printStackTrace();
                    }
                } else if (topic.equals(SHOPPING_LIST_READ)) {
                    feedback(newMessage);
                }
                if (topic.equals(feedbackTopic)) {
                    System.out.println("FEEDBACK: " + newMessage);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
    }

    // ** MQTT Connection **
    // Initialize a client and create a connection to the set broker declared in SERVER_URI.
    private void connect() {
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(getApplicationContext(), SERVER_URI, clientId);
        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected Log.d(TAG, “onSuccess”);
                    System.out.println(TAG + "Success. Connected to " + SERVER_URI);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.d(TAG, "onFailure");
                    System.out.println(TAG + "Oh no! Failed to connect to " + SERVER_URI);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            IMqttToken subToken = client.publish(topic, mqttMessage);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("Publish successful to topic: " + topic);

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    System.out.println("Failed to publish to topic: " + topic);
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    //Sets a subscription to the set topic, using the client from connect().
    private void subscribe(String topicToSubscribe) {
        final String topic = topicToSubscribe;
        int qos = 1;
        try {
            IMqttToken subToken = client.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("Subscription successful to topic: " + topic);

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    System.out.println("Failed to subscribe to topic: " + topic);
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

//    //OLD Controller
//    public void decodeInput(ArrayList<String> result) {
//
//        //TODO: skulle kunna skapa en samling med fraser som är okej? Ev göra det i en egen klass eller typ JSON?
//        String resultString = result.toString().toLowerCase(); //TODO: Move this into onActivityResult ist? Snyggare för användaren
//        if (resultString.contains("turn on the lamp")) {
//            //publish "turnOn" to jarvis/livingroom/floorlamp
//            feedback("Turning on the lamp");
//        } else if (resultString.contains(("turn off the lamp"))) {
//            //publish "turnOff" to jarvis/livingroom/floorlamp
//            feedback("Turning off the lamp");
//        } else {
//            feedback("No valid input, please try again!");
//        }
//    }

    public void feedback(String string) {
        System.out.println(string);
        textToSpeech.speak(string, TextToSpeech.QUEUE_FLUSH, null);
        Toast toast = Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model " + exception.getMessage()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        voiceInput.append(hypothesis + "\n");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        voiceInput.append(hypothesis + "\n");
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        voiceInput.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }


    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void setErrorState(String message) {
        voiceInput.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                voiceInput.setText(R.string.preparing);
                voiceInput.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_READY:
                voiceInput.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_FILE:
                voiceInput.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                voiceInput.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    public void getWeatherDetails(View view) {
        String tempUrl = "";
        String city = cityInput.getText().toString().trim();
        String country = countryInput.getText().toString().trim();



        if (city.equals("")) {
            weatherResult.setText("City field can not be empty!");
        } else {
            if (!country.equals("")) {
                tempUrl = url + "?q=" + city + "," + country + "&appid=" + appid;
            } else {
                tempUrl = url + "?q=" + city + "&appid=" + appid;
            }
            StringRequest stringRequest = new StringRequest(Request.Method.POST, tempUrl, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    String output = "";
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray jsonArray = jsonResponse.getJSONArray("weather");
                        JSONObject jsonObjectWeather = jsonArray.getJSONObject(0);
                        String description = jsonObjectWeather.getString("description");
                        JSONObject jsonObjectMain = jsonResponse.getJSONObject("main");
                        double temp = jsonObjectMain.getDouble("temp") - 273.15;
                        double feelsLike = jsonObjectMain.getDouble("feels_like") - 273.15;
                        JSONObject jsonObjectSys = jsonResponse.getJSONObject("sys");
                        String countryName = jsonObjectSys.getString("country");
                        String cityName = jsonResponse.getString("name");
                        output += "Current weather of " + cityName + " (" + countryName + ")"
                                + "\n Temp: " + Math.round(temp) + " °C"
                                + "\n Feels Like: " + Math.round(feelsLike) + " °C"
                                + "\n Description: " + description;
                        weatherResult.setText(output);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(getApplicationContext(), error.toString().trim(), Toast.LENGTH_SHORT).show();
                }
            });
            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            requestQueue.add(stringRequest);
        }
    }

}