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
import android.text.PrecomputedText;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements
        RecognitionListener {

    private TextToSpeech textToSpeech;
    private MqttAndroidClient client;
    private static final String SERVER_URI = "tcp://test.mosquitto.org:1883";
    private static final String TAG = "MainActivity";

    //TOPICS
    private static final String FLOOR_LAMP = "project-jarvis/floorlamp"; //TODO: Remove
    private static final String ALARM_TOPIC_CREATE = "project-jarvis/alarm/create";
    private static final String TIMER_TOPIC_CREATE = "project-jarvis/timer/create";
    private static final String SHOPPING_LIST_CREATE = "project-jarvis/shopping-list/create"; //TODO: ONÖDIG?
    private static final String SHOPPING_LIST_CONTROL = "project-jarvis/shopping-list/control";
    private static final String SHOPPING_LIST_REMOVE = "project-jarvis/shopping-list/remove";
    private static final String SHOPPING_LIST_READ = "project-jarvis/shopping-list/read";
    private static final String DEVICES_TOPIC = "project-jarvis/devices"; //Used for creating and removing devices

//ALL SUBSCRIPTION TOPICS
    private static final String DEVICES_LIST_TOPIC = "project-jarvis/devices/list"; //lists all available devices
    private static final String FEEDBACK_TOPIC = "project-jarvis/feedback";
    private static final String ALARM_TOPIC_CONTROL = "project-jarvis/alarm/control";
    private static final String TIMER_TOPIC_CONTROL = "project-jarvis/timer/control";
    private static final String[] SUBSCRIPTION_TOPICS = {
            DEVICES_LIST_TOPIC, FEEDBACK_TOPIC,
            ALARM_TOPIC_CONTROL, TIMER_TOPIC_CONTROL
    };
    private ArrayList<String> deviceTopics; //Collection for all of our device topics, updated from server

    //creates the ringtone / alarm
    private boolean ringtoneActive = false;
    private static final Locale SWEDEN = new Locale("sv", "SE");
    private final Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    private Ringtone ringtone;

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


    EditText cityInput;
    TextView weatherResult;
    private final String url = "https://api.openweathermap.org/data/2.5/weather";
    private final String appid = "f867fac52bdc5a485eb681e29989f284";
    //Old: e53301e27efa0b66d05045d91b2742d3
    //New: f867fac52bdc5a485eb681e29989f284


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cityInput = findViewById(R.id.cityInput);
        weatherResult = findViewById(R.id.weatherResult);

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

        //ImageButton voiceBtn = findViewById(R.id.voiceBtn); //button for activating voice recognition
        ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        Button devices = findViewById(R.id.devices);
        devices.setOnClickListener(v -> publish(DEVICES_TOPIC, "deviceName,id,command"));

        ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);

        //TTS OnInit NOTE: Can't set locale on virtual machine
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
                System.out.println("YES INIT");
            } else {
                Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                System.out.println("NO INIT. Probably running on emulator");
            }
        });


        //MQTT Connect

        try {
            mqttConnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }


        devicesBtn.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), Devices.class);
            startActivity(intent);
            System.out.println("Opening view devices!");
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
                alarmControl("stop");
            }
        });
    }

    // Old LINK
    public void mqttConnect() throws MqttException {
        connect();
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    System.out.println("Reconnected to : " + serverURI); // Re-subscribe as we lost it due to new session
                } else {
                    System.out.println("Connected to: " + serverURI);
                }
                for (String topic : SUBSCRIPTION_TOPICS) {
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

                switch (topic) {
                    case ALARM_TOPIC_CONTROL:
                        System.out.println("ALARM ALARM-Control!");
                        try {
                            if (newMessage.contains("play")) {
                                alarmControl("play");
                                feedback("Alarm: " + separatedMessage[1]);
                            } else if (newMessage.contains("stop")) {
                                System.out.println("Turning off alarm");
                                alarmControl("stop");
                            }
                        } catch (Exception e) {
                            System.out.println("Alarm error");
                            e.printStackTrace();
                        }
                        break;
                    case TIMER_TOPIC_CONTROL:
                        System.out.println("TIMER TOPIC-Control");
                        try {
                            if (newMessage.contains("play")) {
                                alarmControl("play");
                                feedback("Time's up!");
                            } else if (newMessage.contains("stop")) {
                                alarmControl("stop");
                            }
                        } catch (Exception e) {
                            System.out.println("Alarm error");
                            e.printStackTrace();
                        }
                        break;
                    case SHOPPING_LIST_READ:
                        feedback(newMessage);
                        break;
                }
                if (topic.equals(FEEDBACK_TOPIC)) {
                    System.out.println("FEEDBACK: " + newMessage);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
    }

    private void alarmControl (String command) {
        ringtoneActive = command.equals("play");
        if (command.equals("play")) {
            System.out.println("Playing alarm sound");
            ringtone.play();
        } else {
            System.out.println("Stopping alarm sound");
            ringtone.stop();
        }
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

    public void decodeInput(String input) {
        if (input.equals("text")) {
            return; //Ignores the basic "text" input if nothing has been heard
        }
        //TODO: skulle kunna skapa en samling med fraser som är okej? Ev göra det i en egen klass eller typ JSON?
        if (input.contains("lamp") && input.contains("on")) {
            publish(FLOOR_LAMP, "device/TurnOn");
            feedback("Turning on the lamp");
        } else if (input.contains("lamp") && ((input.contains("off")) || input.contains("of"))) {
            publish(FLOOR_LAMP, "device/TurnOff");
            feedback("Turning off the lamp");
        } else if (input.contains("set") && input.contains("timer")) {
            long numbers = findNumbers(input);
            System.out.println("NUMBERS ARE: " + numbers);
            String[] keywords = {"set", "timer"};
            String toSend = cleanInput(input, keywords);
            //WHAT SHOULD BE SENT IS: set,timer,(n)time
            System.out.println("TO SEND IS: " + toSend + numbers);
            publish(TIMER_TOPIC_CREATE, toSend + numbers);
        } else if (input.contains("set") || input.contains("create") && input.contains("alarm")) {
            System.out.println("CREATE ALARM");
            System.out.println("CREATED ALARM: " + createAlarm(input));
            //TODO: String STRING = createAlarm(input);
//TODO:            publish(ALARM_TOPIC_CREATE, STRING:(name,every-day,8:00));
        } else if (input.contains("alarm") && (input.contains("off") || input.contains("of"))) {
            alarmControl("stop");
        } else if (input.contains("what") && input.contains("time") && ((input.contains("is it") || input.contains("is the")) || input.contains("'s the"))) {
            feedback("The time is " + stringTime(getCurrentTime()));
        } else if (input.contains("what") && (input.contains("is the") || input.contains("'s the")) && input.contains("weather")) {
            System.out.println("WEATHER");
            cityInput.setText(input.substring(input.lastIndexOf(" ") + 1));
            getWeatherDetails();
            //feedback(weatherResult.toString());
        } else {
            feedback("No valid input, please try again!");
        }
    }

    private String findName(String input) {
        String name = "noName";
        String patternString = "";
        boolean match = false;
        //TODO: Stop name after "and"?
        if (input.contains("named")) {
            patternString = "named";
            match = true;
        } else if (input.contains("name")) {
            patternString = "name";
            match = true;
        }
        if (match) {
            Pattern namePattern = Pattern.compile(patternString +"(.*)");
            Matcher nameMatcher = namePattern.matcher( input );
            if ( nameMatcher.find() ) {
                name = nameMatcher.group(1); // " that is awesome"
                System.out.println("Named: " + name);
            }
        }
        return name;
    }

    //TODO: This is what'll be read aloud when asking for the time
    private String readTime(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm");
        return format.format(date);
    }

    //Returns a String for the time instead of a Date object
    private String stringTime(Calendar calendar) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", SWEDEN);
        System.out.println("STRINGTIME IS: " + format.format(calendar));
        //TODO: Decode the string into "DAY, the DATE" etc
        return format.format(calendar);
    }

    //Gets the current local time in Stockholm
    //TODO: REMOVE?
    private Calendar getCurrentTime() {
        TimeZone timeZone = TimeZone.getTimeZone("Europe/Stockholm");
        TimeZone.setDefault(timeZone);
        return Calendar.getInstance(timeZone, SWEDEN);
    }

    //takes a String and the Keywords for that string, returns String with only the keywords
    private String cleanInput(String input, String[] keywords) {
        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("(?i)\\b(");
        for (int i = 0; i < keywords.length; i++) {
            if (i == 0) {
                regexBuilder.append(keywords[i]);
            } else {
                regexBuilder.append("|");
                regexBuilder.append(keywords[i]);
            }
        }
        regexBuilder.append(")\\b");
        System.out.println("Keywords in Regex are: " + regexBuilder.toString());
        Pattern pattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = pattern.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            output.append(matcher.group()).append(",");
        }
        return output.toString();
    }

    public void feedback(String string) {
        System.out.println(string);
        textToSpeech.speak(string, TextToSpeech.QUEUE_FLUSH, null);
        Toast toast = Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT);
        toast.show();
    }

    //Only accepts time in 24h-format
    //TODO: ALARM-METHOD
    private String createAlarm(String input) {
        //TODO: GOAL: tomorrow,17:00 - use cleanInput!
        //TODO: name,every-day,8:00
        String alarmName = findName(input); //name of the alarm, if it has one
        long inputNumbers = findNumbers(input); //amount of seconds to alarm
        int addSeconds = (int) inputNumbers;
        Calendar calendar = getCurrentTime();
        System.out.println("Calendar time is " + calendar.getTime().toString());
        calendar.add(Calendar.SECOND, addSeconds);
        long secToAlarm = (calendar.getTimeInMillis()) / 1000;
        int alarmLength = (int)secToAlarm;

        System.out.println("ALARMLENGTH IS: " + alarmLength);
        System.out.println("Alarm is set for " + calendar.getTime().toString());
        String[] day = decodeDay(input);
        String[] keywords = {alarmName.trim(), String.valueOf(alarmLength), day[0], day[1]};

        //"SHOULD RETURN: AlarmName, AlarmLength, Repeatable > WHEN";
        //wake up,1301241,every,tuesday
        String alarmMessage = cleanInput(input, keywords);
        return alarmMessage + alarmLength;
    }

    private String[] decodeDay (String input) {
        String[] days = {
                "today", "tomorrow", "monday", "tuesday", "wednesday", "thursday", "friday",
                "saturday", "sunday"
        };
        String[] specialCommands = {
                "next", "every", "every second", "every third", "once a" //can be expanded...
        };
        String special = "noSpecial";
        String day = "noDay";
        for (String str : days) {
            if (input.contains(str)) {
                day = str;
            }
        }
        for (String str : specialCommands) {
            if (input.contains(str)) {
                special = str;
            }
        }
        return new String[]{special, day};
    }

    //Finds and unpacks the VOSK model
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

    //Handles the word input and sends it to decodeInput()
    @Override
    public void onResult(String hypothesis) {
        voiceInput.append(hypothesis + "\n");
        String word = filter(hypothesis);
        System.out.println("WORD: " + word);
        decodeInput(word);
    }

    //Removes "text" from the String and the JSON-esque styling
    private static String filter(String input) {
        String cleanStr = input.replaceAll("[^A-Za-z0-9' ]", "").replaceAll(" +", " ");
        String[] words = cleanStr.trim().split(" ", 2);
        if (words.length > 0) {
            return words[words.length - 1];
        }
        return "ERROR, try again";
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

    public void getWeatherDetails() {
        String city = cityInput.getText().toString().trim();
        String tempUrl = url + "?q=" + city + "&appid=" + appid;

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
                    String cityName = jsonResponse.getString("name");
                    output += "Current weather in " + cityName
                            + "\n Temp: " + Math.round(temp) + " °C"
                            + "\n Feels Like: " + Math.round(feelsLike) + " °C"
                            + "\n Sky: " + description.substring(0,1).toUpperCase() + description.substring(1).toLowerCase();
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

    private long findNumbers(String input) {
        double multiplier = 1; //Amount of seconds the numbers are worth
        long result = 0;
        long output = 0;

        //TODO: IF input contains "at 8" / one of the days, fetch a calendar time instead?

        //TODO: OR, maybe make findNumbers return a long[]?

        List<String> allowedStrings = Arrays.asList
                (
                        "zero", "one", "two", "three", "four", "five", "six", "seven",
                        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
                        "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
                        "hundred", "thousand", "million", "billion", "trillion"
                );

        String[] days = {
                "today", "tomorrow", "monday", "tuesday", "wednesday", "thursday", "friday",
                "saturday", "sunday"
        };

        if (input.contains("in") || input.contains("from now")) {
            if (input.contains("hour")) {
                multiplier = 3600;
            } else if (input.contains("minute")) {
                multiplier = 60;
            } else if (input.contains("day") || input.contains("days")) {
                boolean weekdayFound = false;
                for (String day : days) {
                    if (input.contains(day)) {
                        weekdayFound = true;
                        break;
                    }
                }
                if (!weekdayFound) {
                    multiplier = 86400;
                }
            } else if (input.contains("week")) {
                multiplier = 604800;
            } else if (input.contains("month")) {
                multiplier = 2629743.83;
            } else if (input.contains("year")) {
                multiplier = 31556926;
            }
        }

        String[] words = input.trim().split("\\s+");
        for (String str : words) {
            if (allowedStrings.contains(str)) {
                if (str.equalsIgnoreCase("zero")) {
                    result += 0;
                } else if (str.equalsIgnoreCase("one")) {
                    result += 1;
                } else if (str.equalsIgnoreCase("two")) {
                    result += 2;
                } else if (str.equalsIgnoreCase("three")) {
                    result += 3;
                } else if (str.equalsIgnoreCase("four")) {
                    result += 4;
                } else if (str.equalsIgnoreCase("five")) {
                    result += 5;
                } else if (str.equalsIgnoreCase("six")) {
                    result += 6;
                } else if (str.equalsIgnoreCase("seven")) {
                    result += 7;
                } else if (str.equalsIgnoreCase("eight")) {
                    result += 8;
                } else if (str.equalsIgnoreCase("nine")) {
                    result += 9;
                } else if (str.equalsIgnoreCase("ten")) {
                    result += 10;
                } else if (str.equalsIgnoreCase("eleven")) {
                    result += 11;
                } else if (str.equalsIgnoreCase("twelve")) {
                    result += 12;
                } else if (str.equalsIgnoreCase("thirteen")) {
                    result += 13;
                } else if (str.equalsIgnoreCase("fourteen")) {
                    result += 14;
                } else if (str.equalsIgnoreCase("fifteen")) {
                    result += 15;
                } else if (str.equalsIgnoreCase("sixteen")) {
                    result += 16;
                } else if (str.equalsIgnoreCase("seventeen")) {
                    result += 17;
                } else if (str.equalsIgnoreCase("eighteen")) {
                    result += 18;
                } else if (str.equalsIgnoreCase("nineteen")) {
                    result += 19;
                } else if (str.equalsIgnoreCase("twenty")) {
                    result += 20;
                } else if (str.equalsIgnoreCase("thirty")) {
                    result += 30;
                } else if (str.equalsIgnoreCase("forty")) {
                    result += 40;
                } else if (str.equalsIgnoreCase("fifty")) {
                    result += 50;
                } else if (str.equalsIgnoreCase("sixty")) {
                    result += 60;
                } else if (str.equalsIgnoreCase("seventy")) {
                    result += 70;
                } else if (str.equalsIgnoreCase("eighty")) {
                    result += 80;
                } else if (str.equalsIgnoreCase("ninety")) {
                    result += 90;
                } else if (str.equalsIgnoreCase("hundred")) {
                    result *= 100;
                } else if (str.equalsIgnoreCase("thousand")) {
                    result *= 1000;
                    output += result;
                    result = 0;
                } else if (str.equalsIgnoreCase("million")) {
                    result *= 1000000;
                    output += result;
                    result = 0;
                } else if (str.equalsIgnoreCase("billion")) {
                    result *= 1000000000;
                    output += result;
                    result = 0;
                } else if (str.equalsIgnoreCase("trillion")) {
                    result *= 1000000000000L;
                    output += result;
                    result = 0;
                }
            }
        }
        output += result * multiplier;
        System.out.println("Number result: " + output);
        return output;
    }
}