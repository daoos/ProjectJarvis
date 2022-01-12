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
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final String FLOOR_LAMP = "project-jarvis/device/floor-lamp"; //TODO: Remove
    private static final String ALARM_TOPIC_CREATE = "project-jarvis/alarm/create";
    private static final String TIMER_TOPIC_CREATE = "project-jarvis/timer/create";
    private static final String SHOPPING_LIST_CREATE = "project-jarvis/shopping-list/create"; //TODO: ONÖDIG?
    private static final String SHOPPING_LIST_DELETE = "project-jarvis/shopping-list/delete";
    private static final String SHOPPING_LIST_REMOVE = "project-jarvis/shopping-list/remove";
    private static final String SHOPPING_LIST_READ = "project-jarvis/shopping-list/read";
    private static final String SHOPPING_LIST_READ_ALL = "project-jarvis/shopping-list/read-all";
    private static final String SHOPPING_LIST_ADD = "project-jarvis/shopping-list/add";

    //ALL SUBSCRIPTION TOPICS
    private static final String DEVICES_LIST_TOPIC = "project-jarvis/devices/list"; //lists all available devices
    private static final String FEEDBACK_TOPIC = "project-jarvis/feedback";
    private static final String ALARM_TOPIC_CONTROL = "project-jarvis/alarm/control";
    private static final String TIMER_TOPIC_CONTROL = "project-jarvis/timer/control";
    private static final String[] SUBSCRIPTION_TOPICS = {
            DEVICES_LIST_TOPIC, FEEDBACK_TOPIC, ALARM_TOPIC_CONTROL, TIMER_TOPIC_CONTROL
    };
    //Collection for all of our device topics, updated from server using unpackDevices()
    private final HashMap<String, String> deviceTopicsMap = new HashMap<>();

    //creates the ringtone / alarm
    private Button alarmBtn;
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

    private EditText cityInput;
    private TextView weatherResult;
    //Weather API stuff here


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
        turnOnBtn.setOnClickListener(v -> publish(FLOOR_LAMP, "device/turnOn,0,seconds"));

        Button turnOffBtn = findViewById(R.id.turnOffBtn);
        turnOffBtn.setOnClickListener(v -> publish(FLOOR_LAMP, "device/turnOff,0,seconds"));

        //ImageButton devicesBtn = findViewById(R.id.devicesBtn); //Devices button

        Button devices = findViewById(R.id.devices);

        ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);

        //TTS OnInit
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


        devices.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), Devices.class);
            startActivity(intent);
            System.out.println("Opening view devices!");
        });

        alarmBtn = findViewById(R.id.alarmBtn);
        alarmBtn.setEnabled(ringtoneActive);
        alarmBtn.setOnClickListener(v -> {
            if (ringtoneActive) {
                alarmControl("stop");
            } else {
                feedback("No active alarm");
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
                    case DEVICES_LIST_TOPIC:
                        unpackDevices(separatedMessage);
                        break;
                    //TODO: REMOVE, Use feedback-topic instead
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
                            System.out.println("Alarm error" + e.toString());
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
                }
                if (topic.equals(FEEDBACK_TOPIC)) {
                    //TODO: Control so only feedbacks relevant stuff
                    feedback(newMessage);
                    System.out.println("FEEDBACK: " + newMessage);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
    }

    private void unpackDevices(String[] separatedMessage) {
        for (String topic : separatedMessage) {
            String name = topic.substring(topic.lastIndexOf("/") + 1);
            name = name.replaceAll("-", " ");
            deviceTopicsMap.put(name, topic);
        }
    }

    private void alarmControl(String command) {
        ringtoneActive = command.equals("play");
        if (command.equals("play")) {
            System.out.println("Playing alarm sound");
            ringtone.play();
            ringtoneActive = true;
            alarmBtn.setEnabled(ringtoneActive);
        } else {
            System.out.println("Stopping alarm sound");
            ringtone.stop();
            ringtoneActive = false;
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
                    System.out.println("Publish successful to topic: " + topic +
                            " message: " + message);
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

        boolean published = false;
        String timeCommand = "0";
        String timeUnit = "second";
        Set<String> deviceNames = deviceTopicsMap.keySet();
        for (String deviceName : deviceNames) {
            if (input.contains(deviceName)) {
                String message = "device/info";
                if ((input.contains("turn") || input.contains("switch"))) {
                    if (input.contains("off") || input.contains("of")) {
                        message = "device/TurnOff";
                    } else if (input.contains("on")) {
                        message = "device/TurnOn";
                    }
                } else if (input.contains("ring")) {
                    message = "device/bell";
                } else if (input.contains("rename") || (input.contains("name")
                        && input.contains("change"))) {
                    String newName = deviceName;
                    if (input.contains("to")) {
                        newName = findWordsAfter(input, "to ");
                    }
                    message = "device/setName" + "," + newName;
                } else if (input.contains("dim") || input.contains("turn up")
                        || input.contains("turn down")) {
                    feedback("Dimming is not yet implemented");
                } else if (input.contains("down")) {
                    feedback("Command 'down' is not yet implemented");
                } else if (input.contains("history")) {
                    feedback("Device history is not yet implemented");
                } else if (input.contains("info")) {
                    feedback("See the server log for device info.");
                } else if (input.contains("learn")) {
                    feedback("Command 'learn' is not yet implemented");
                } else if (input.contains("rbg") || input.contains("color")) {
                    feedback("Command 'rgb' is not yet implemented");
                } else if (input.contains("add") || input.contains("group") ||
                        input.contains("remove") || input.contains("ignore")) {
                    feedback("To handle your devices, please log in to your" +
                            "account on live.telldus.com");
                }
                if (message.contains("device/")) {
                    if (input.contains(" in ")) {
//                        Object[] numbers = findNumbers(findWordsAfter(input, " in "));
                        Object[] numbers = findNumbers(input);
                        timeCommand = String.valueOf(numbers[0]);
                        timeUnit = (String) numbers[1];
                    } else if (input.contains(" after ")) {
//                        Object[] numbers = findNumbers(findWordsAfter(input, " after "));
                        Object[] numbers = findNumbers(input);
                        timeCommand = String.valueOf(numbers[0]);
                        timeUnit = (String) numbers[1];
                    }
                    publish(deviceTopicsMap.get(deviceName), message + "," +
                            timeCommand + "," + timeUnit);
                    published = true;
                }
            }
        }

        if (!published) {
            if (input.contains("set") && input.contains("timer")) {
                Object[] getNumbers = findNumbers(input);
                long numbers = (long) getNumbers[0];
                String[] keywords = {"set", "timer"};
                String toSend = cleanInput(input, keywords);
                publish(TIMER_TOPIC_CREATE, toSend + numbers);
            } else if ((input.contains("set") || input.contains("create"))
                    && input.contains("alarm")) {
                System.out.println("CREATED ALARM: " + createAlarm(input));
                //TODO: String STRING = createAlarm(input);
//TODO:            publish(ALARM_TOPIC_CREATE, STRING:(name,every-day,8:00));
            } else if (input.contains("alarm") && (input.contains("off")
                    || input.contains("of"))) {
                alarmControl("stop");
            } else if (input.contains("what") && input.contains("time") &&
                    ((input.contains("is it") || input.contains("is the"))
                            || input.contains("'s the"))) {
                feedback("The time is " + stringTime(getCurrentTime()));
            } else if (input.contains("what") && (input.contains("is the")
                    || input.contains("'s the")) && input.contains("weather")) {
                String cityName = findWordsAfter(input, "in");
                if (!cityName.equals("")) {
                    cityInput.setText(cityName); //detect city
                }
                getWeatherDetails();
            } else if (input.contains("shopping list")) {
                if (input.contains("create") || input.contains("make")) {
                    String listName = findName(input);
                    publish(SHOPPING_LIST_CREATE, listName);
                } else if (input.contains("delete") || input.contains("remove")) {
                    String listName = findName(input);
                    if (!input.contains("from")) {
                        publish(SHOPPING_LIST_DELETE, listName);
                    } else {
                        String item = "nothing";
                        String strAmount = "all";
                        Object[] numbers = findNumbers(input);
                        long amount = (long) numbers[0];
                        if (amount != 0) {
                            strAmount = String.valueOf(amount);
                            ;
                        }
                        if (!String.valueOf(amount).equals("0")) {
                            item = findWordsBetween(input, (String) numbers[2], "from");
                        } else if (input.contains(" a ")) {
                            item = findWordsBetween(input, "a", "from");
                        } else if (input.contains(" the ")) {
                            item = findWordsBetween(input, "the", "from");
                        } else if (input.contains(" an ")) {
                            item = findWordsBetween(input, "an", "from");
                        } else {
                            item = findWordsBetween(input, "remove", "from");
                        }
                        String name = findName(input);
                        System.out.println("To be removed is: " + strAmount + " " + item);
                        String[] keywords = {name, item};
                        String command = cleanInput(input, keywords) + "," + strAmount;
                        publish(SHOPPING_LIST_REMOVE, command);
                    }
                } else if (input.contains("list all") || input.contains("read all")) {
                    publish(SHOPPING_LIST_READ_ALL, "list all");
                } else if (input.contains(" read ")) {
                    String name = findName(input);
                    publish(SHOPPING_LIST_READ, name);
                } else if (input.contains("add") || input.contains("put")) {
                    //TODO: Fix
                    String item;
                    String strAmount;
                    Object[] numbers = findNumbers(input);
                    long amount = (long) numbers[0];
                    if (!String.valueOf(amount).equals("0")) {
                        item = findWordsBetween(input, (String) numbers[2], "to");
                    } else if (input.contains(" the ")) {
                        item = findWordsBetween(input, "the", "from");
                    } else if (input.contains(" a ")) {
                        item = findWordsBetween(input, "a", "to");
                    } else if (input.contains(" an ")) {
                        item = findWordsBetween(input, "an", "to");
                    } else {
                        item = findWordsBetween(input, "add", "to");
                    }
                    String name = findName(input);
                    strAmount = String.valueOf(amount);
                    System.out.println("To be added is: " + strAmount + " " + item);
//                    String[] keywords = {name, item};
//                    String command = cleanInput(input, keywords) + "," + strAmount;
//                    publish(SHOPPING_LIST_ADD, command);
                    publish(SHOPPING_LIST_ADD, name + "," + item + "," + strAmount);
                }
            } else {
                feedback("No valid input, please try again!");
            }
        }
    }

    private String findWordsBetween(String input, String start, String end) {
        String regexString = Pattern.quote(start) + "(.*?)" + Pattern.quote(end);
        Pattern pattern = Pattern.compile(regexString);
        Matcher matcher = pattern.matcher(input);
        StringBuilder stringBuilder = new StringBuilder();
        while (matcher.find()) {
            stringBuilder.append(matcher.group());
        }
        String output = stringBuilder.toString();
        output = output.replaceFirst(start, "");
        output = output.substring(0, output.lastIndexOf(" "));
        return output;
    }

    private String findWordsAfter(String input, String start) {
        String output = "";
        Pattern namePattern = Pattern.compile(start + "(.*)");
        Matcher nameMatcher = namePattern.matcher(input);
        if (nameMatcher.find()) {
            output = nameMatcher.group(1);
        }
        assert output != null;
        return output.trim();
    }

    private String findName(String input) {
        String name = "no name";
        if (input.contains("named ")) {
            if (!input.contains("and")) {
                name = findWordsAfter(input, "named");
            } else {
                name = findWordsBetween(input, "named", "and");
            }
        } else if (input.contains("name ")) {
            if (!input.contains("and")) {
                name = findWordsAfter(input, "name");
            } else {
                name = findWordsBetween(input, "name", "and");
            }
        }
        return name.trim();
    }

    //TODO: This is what'll be read aloud when asking for the time should change 06-03 to "3rd of june" etc
    private String readTime(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm");
        return format.format(date);
    }

    //Returns a String for the time instead of a Date object
    private String stringTime(Calendar calendar) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", SWEDEN);
        //TODO: Decode the string into "DAY, the DATE" etc
        return format.format(calendar.getTime());
    }

    //Gets the current local time in Stockholm, regardless of saving-time
    //TODO: REMOVE?
    private Calendar getCurrentTime() {
        TimeZone timeZone = TimeZone.getTimeZone("Europe/Stockholm");
        TimeZone.setDefault(timeZone);
        return Calendar.getInstance(timeZone, SWEDEN);
    }

    //takes a String and the Keywords for that string, returns String with only the keywords separated by comma
    private String cleanInput(String input, String[] keywords) {
        StringBuilder regexBuilder = new StringBuilder();
        StringBuilder outputBuilder = new StringBuilder();
        input = input.replace("/", ":");
        regexBuilder.append("(?i)\\b(");
        for (int i = 0; i < keywords.length; i++) {
            if (i != 0) {
                regexBuilder.append("|");
            }
            regexBuilder.append(keywords[i]);
            System.out.println("KEYWORD: " + keywords[i]);
        }
        regexBuilder.append(")\\b");
        System.out.println("Keywords in Regex are: " + regexBuilder.toString());
        System.out.println("MESSAGE IS: " + input);
        Pattern pattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            outputBuilder.append(matcher.group());
            outputBuilder.append(",");
            System.out.println("Matcher group is : " + matcher.group());
        }
        String output = outputBuilder.toString();
        output = output.replace(":", "/");
        return output;
    }

    public void feedback(String string) {
        System.out.println(string);
        textToSpeech.speak(string, TextToSpeech.QUEUE_FLUSH, null);
        Toast toast = Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT);
        toast.show();
    }

    //Only accepts time in 24h-format
    //TODO: ALARM-METHOD, doesn't quite work...
    private String createAlarm(String input) {
        //TODO: GOAL: tomorrow,17:00 - use cleanInput!
        //TODO: name,every-day,8:00
        String alarmName = findName(input); //name of the alarm, if it has one
        Object[] inputNumbers = findNumbers(input); //amount of seconds to alarm
        long inputLong = (long) inputNumbers[0];
        int addSeconds = (int) inputLong; //TODO: FIX

        String unit = (String) inputNumbers[1]; //TODO: Should return second/hour/day etc

        Calendar calendar = getCurrentTime();
        System.out.println("CreateAlarm(): Calendar time is " + calendar.getTime().toString());
        calendar.add(Calendar.SECOND, addSeconds);
        long secToAlarm = (calendar.getTimeInMillis()) / 1000;
        int alarmLength = (int) secToAlarm;

        System.out.println("CreateAlarm(): ALARMLENGTH IS " + alarmLength);
        System.out.println("CreateAlarm(): Alarm is set for " + calendar.getTime().toString());
        String[] day = decodeDay(input);
        String[] keywords = {alarmName.trim(), String.valueOf(alarmLength), day[0], day[1]};

        //"SHOULD RETURN: AlarmName, AlarmLength, Repeatable > WHEN";
        //wake up,1301241,every,tuesday
        String alarmMessage = cleanInput(input, keywords);
        return alarmMessage + alarmLength;
    }

    private String[] decodeDay(String input) {
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

    //VOSK START
    //Finds and unpacks the VOSK model
    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model " + exception.getMessage()));
    }

    //Handles the permissionrequest for recording audio
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
        System.out.println("Result: " + word);
        decodeInput(word);
    }

    //Removes "text" from the String and the JSON-esque styling
    //TODO: was static, why?
    private String filter(String input) {
        String cleanStr = input.replaceAll("[^A-Za-z0-9' ]", "")
                .replaceAll(" +", " ");
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
    // VOSK END

    public void getWeatherDetails() {
        String city = cityInput.getText().toString().trim();
        String tempUrl = URL + "?q=" + city + "&appid=" + APP_ID;

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
                            + "\n Sky: " + description.substring(0, 1).toUpperCase()
                            + description.substring(1).toLowerCase();
                    weatherResult.setText(output);
                    feedback(output);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getApplicationContext(), error.toString().trim(),
                        Toast.LENGTH_SHORT).show();
            }
        });
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        requestQueue.add(stringRequest);
    }

    private Object[] findNumbers(String input) {
        double multiplier = 1; //Amount of seconds the numbers are worth
        long result = 0;
        long output = 0;
        StringBuilder numString = new StringBuilder();
        String unit = "second";
        Object[] returnResult = {output, unit, ""};

        //TODO: IF input contains "at 8" / one of the days, fetch a calendar time instead?

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

        if (input.contains(" in ") || input.contains("from now") || input.contains(" at ")) {
            if (input.contains("hour")) {
                multiplier = 3600;
                unit = "hour";
            } else if (input.contains("minute")) {
                multiplier = 60;
                unit = "minute";
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
                    unit = "day";
                }
            } else if (input.contains("week")) {
                multiplier = 604800;
                unit = "week";
            } else if (input.contains("month")) {
                multiplier = 2629743.83;
                unit = "month";
            } else if (input.contains("year")) {
                multiplier = 31556926;
                unit = "year";
            }
        }

        String[] words = input.trim().split("\\s+");
        for (String str : words) {
            if (allowedStrings.contains(str)) {
                switch (str) {
                    case "zero":
                        result += 0;
                        numString.append(" zero ");
                        break;
                    case "one":
                        result += 1;
                        numString.append(" one ");
                        break;
                    case "two":
                        result += 2;
                        numString.append(" two ");
                        break;
                    case "three":
                        result += 3;
                        numString.append(" three ");
                        break;
                    case "four":
                        result += 4;
                        numString.append(" four ");
                        break;
                    case "five":
                        result += 5;
                        numString.append(" five ");
                        break;
                    case "six":
                        result += 6;
                        numString.append(" six ");
                        break;
                    case "seven":
                        result += 7;
                        numString.append(" seven ");
                        break;
                    case "eight":
                        result += 8;
                        numString.append(" eight ");
                        break;
                    case "nine":
                        result += 9;
                        numString.append(" nine ");
                        break;
                    case "ten":
                        result += 10;
                        numString.append(" ten ");
                        break;
                    case "eleven":
                        result += 11;
                        numString.append(" eleven ");
                        break;
                    case "twelve":
                        result += 12;
                        numString.append(" twelve ");
                        break;
                    case "thirteen":
                        result += 13;
                        numString.append(" thirteen ");
                        break;
                    case "fourteen":
                        result += 14;
                        numString.append(" fourteen ");
                        break;
                    case "fifteen":
                        result += 15;
                        numString.append(" fifteen ");
                        break;
                    case "sixteen":
                        result += 16;
                        numString.append(" sixteen ");
                        break;
                    case "seventeen":
                        result += 17;
                        numString.append(" seventeen ");
                        break;
                    case "eighteen":
                        result += 18;
                        numString.append(" eighteen ");
                        break;
                    case "nineteen":
                        result += 19;
                        numString.append(" nineteen ");
                        break;
                    case "twenty":
                        result += 20;
                        numString.append(" twenty ");
                        break;
                    case "thirty":
                        result += 30;
                        numString.append(" thirty ");
                        break;
                    case "forty":
                        result += 40;
                        numString.append(" forty ");
                        break;
                    case "fifty":
                        result += 50;
                        numString.append(" fifty ");
                        break;
                    case "sixty":
                        result += 60;
                        numString.append(" sixty ");
                        break;
                    case "seventy":
                        result += 70;
                        numString.append(" seventy ");
                        break;
                    case "eighty":
                        result += 80;
                        numString.append(" eighty ");
                        break;
                    case "ninety":
                        result += 90;
                        numString.append(" ninety ");
                        break;
                    case "hundred":
                        result *= 100;
                        numString.append(" hundred ");
                        break;
                    case "thousand":
                        result *= 1000;
                        output += result;
                        result = 0;
                        numString.append(" thousand ");
                        break;
                    case "million":
                        result *= 1000000;
                        output += result;
                        result = 0;
                        numString.append(" million ");
                        break;
                    case "billion":
                        result *= 1000000000;
                        output += result;
                        result = 0;
                        numString.append(" billion ");
                        break;
                    case "trillion":
                        result *= 1000000000000L;
                        output += result;
                        result = 0;
                        numString.append(" trillion ");
                        break;
                }
            }
        }
        output += result * multiplier;
        returnResult[0] = output;
        returnResult[1] = unit;
        if (numString.length() != 0) {
            returnResult[2] = numString.toString();
        }
        return returnResult;
    }
}
