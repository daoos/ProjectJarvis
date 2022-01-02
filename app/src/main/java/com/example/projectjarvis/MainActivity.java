package com.example.projectjarvis;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements
        RecognitionListener {

    private TextToSpeech textToSpeech;
    private static final String FLOOR_LAMP = "project-jarvis/floor-lamp";
    private MqttAndroidClient client;
    private static final String SERVER_URI = "tcp://test.mosquitto.org:1883";
    private static final String TAG = "MainActivity";

    //creates the ringtone / alarm
    private boolean ringtoneActive = false;
    private static final String ALARM_TOPIC_CREATE = "project-jarvis/alarm/create";
    private static final String ALARM_TOPIC_CONTROL = "project-jarvis/alarm/control";
    private static final String TIMER_TOPIC_CREATE = "project-jarvis/timer/create";
    private static final String TIMER_TOPIC_CONTROL = "project-jarvis/timer/control";
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //------Vosk
        voiceInput = findViewById(R.id.voiceInput); //textview for showing the voice input
        setUiState(STATE_START);

        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

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

//        voiceBtn.setOnClickListener(v -> {
//            getSpeechInput(v.getRootView()); //activates voice recog when clicking
//        });

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 10:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    voiceInput.setText(result.get(0));
//                    decodeInput(result); CURRENTLY UNUSED
                }
                break;
        }
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
                subscribe(TIMER_TOPIC_CONTROL);
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
                } else if (topic.equals(TIMER_TOPIC_CONTROL)){
                    System.out.println("TIMER TOPIC-Control");
                    try {
                        if (newMessage.contains("play")) {
                            System.out.println("Playing timer");
                            r.play();
                            ringtoneActive = true;
                            feedback("Time's up!");
                        } else if (newMessage.contains("stop")) {
                            System.out.println("Turning off timer");
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

    public void decodeInput(String result) {
        if (result.equals("text")) {
            return; //Ignores the basic "text" input if nothing has been heard
        }
        //TODO: skulle kunna skapa en samling med fraser som är okej? Ev göra det i en egen klass eller typ JSON?
        if (result.contains("lamp") && result.contains("on")) {
            publish(FLOOR_LAMP, "device/TurnOn");
            feedback("Turning on the lamp");
        } else if (result.contains("lamp") && (result.contains("off")) || result.contains("of")) {
            publish(FLOOR_LAMP, "device/TurnOff");
            feedback("Turning off the lamp");
        } else if (result.contains("set") && result.contains("timer")) {
            long numbers = checkNumbers(result);
            System.out.println("NUMBERS ARE: " + numbers);
            String toSend = cleanInput(result, "Set timer");
            //WHAT SHOULD BE SENT IS: set,timer,(n)time
            System.out.println("TO SEND IS: " + toSend + numbers);
            publish(TIMER_TOPIC_CREATE, toSend + numbers);
        } else {
            feedback("No valid input, please try again!");
        }
    }

    private String cleanInput(String input, String keywords) {
        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("(?i)\\b(");
        String[] keyWordList = keywords.split(" ");
        for (int i = 0; i < keyWordList.length; i++) {
            if (i == 0) {
                regexBuilder.append(keyWordList[i]);
            } else {
                regexBuilder.append("|");
                regexBuilder.append(keyWordList[i]);
            }
        }
        regexBuilder.append(")\\b");
        System.out.println(regexBuilder.toString());
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
        String word = filter(hypothesis);
        System.out.println("WORD: " + word);
        decodeInput(word);
    }

    private static String filter(String input) {
        //Remove "text" from the String and the JSON-esque styling
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
        //findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                voiceInput.setText(R.string.preparing);
                voiceInput.setMovementMethod(new ScrollingMovementMethod());
                //findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                voiceInput.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                //findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                //((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                //findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_FILE:
                //((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                voiceInput.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                //findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                voiceInput.setText(getString(R.string.say_something));
                //findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }


    private long checkNumbers(String input) {
        double multiplier = 1; //Amount of seconds the numbers are worth
        long result = 0;
        long output = 0;

        List<String> allowedStrings = Arrays.asList
                (
                        "zero", "one", "two", "three", "four", "five", "six", "seven",
                        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
                        "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
                        "hundred", "thousand", "million", "billion", "trillion"
                );

        if (input != null && input.length() > 0) {
            String[] words = input.trim().split("\\s+");

            for (String str : words) {
                if (str.contains("minute")) {
                    multiplier = 60;
                } else if (str.contains("hour")) {
                    multiplier = 3600;
                } else if (str.contains("day")) {
                    multiplier = 86400;
                } else if (str.contains("week")) {
                    multiplier = 604800;
                } else if (str.contains("month")) {
                    multiplier = 2629743.83;
                } else if (str.contains("year")) {
                    multiplier = 31556926;
                }
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
        }
        return output;
    }

//    BACKUP
//    private long checkForNumbers(String input) {
//        boolean isValidInput = true;
//        long result = 0;
//        long finalResult = 0;
//        StringBuilder wordInput = new StringBuilder();
//
//        List<String> allowedStrings = Arrays.asList
//                (
//                        "zero", "one", "two", "three", "four", "five", "six", "seven",
//                        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
//                        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
//                        "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
//                        "hundred", "thousand", "million", "billion", "trillion"
//                );
//
//        if (input != null && input.length() > 0) {
//            input = input.replaceAll("-", " ");
//            input = input.toLowerCase().replaceAll(" and", " ");
//            String[] splittedParts = input.trim().split("\\s+");
//
//            for (String str : splittedParts) {
//                if (!allowedStrings.contains(str)) {
//                    isValidInput = false;
//                    System.out.println("Word found : " + str);
//                    wordInput.append(str + " ");
//                } else {
//
//                }
//            }
//            if (isValidInput) {
//                System.out.println("VALID INPUT!");
//                for (String str : splittedParts) {
//                    if (str.equalsIgnoreCase("zero")) {
//                        result += 0;
//                    } else if (str.equalsIgnoreCase("one")) {
//                        result += 1;
//                    } else if (str.equalsIgnoreCase("two")) {
//                        result += 2;
//                    } else if (str.equalsIgnoreCase("three")) {
//                        result += 3;
//                    } else if (str.equalsIgnoreCase("four")) {
//                        result += 4;
//                    } else if (str.equalsIgnoreCase("five")) {
//                        result += 5;
//                    } else if (str.equalsIgnoreCase("six")) {
//                        result += 6;
//                    } else if (str.equalsIgnoreCase("seven")) {
//                        result += 7;
//                    } else if (str.equalsIgnoreCase("eight")) {
//                        result += 8;
//                    } else if (str.equalsIgnoreCase("nine")) {
//                        result += 9;
//                    } else if (str.equalsIgnoreCase("ten")) {
//                        result += 10;
//                    } else if (str.equalsIgnoreCase("eleven")) {
//                        result += 11;
//                    } else if (str.equalsIgnoreCase("twelve")) {
//                        result += 12;
//                    } else if (str.equalsIgnoreCase("thirteen")) {
//                        result += 13;
//                    } else if (str.equalsIgnoreCase("fourteen")) {
//                        result += 14;
//                    } else if (str.equalsIgnoreCase("fifteen")) {
//                        result += 15;
//                    } else if (str.equalsIgnoreCase("sixteen")) {
//                        result += 16;
//                    } else if (str.equalsIgnoreCase("seventeen")) {
//                        result += 17;
//                    } else if (str.equalsIgnoreCase("eighteen")) {
//                        result += 18;
//                    } else if (str.equalsIgnoreCase("nineteen")) {
//                        result += 19;
//                    } else if (str.equalsIgnoreCase("twenty")) {
//                        result += 20;
//                    } else if (str.equalsIgnoreCase("thirty")) {
//                        result += 30;
//                    } else if (str.equalsIgnoreCase("forty")) {
//                        result += 40;
//                    } else if (str.equalsIgnoreCase("fifty")) {
//                        result += 50;
//                    } else if (str.equalsIgnoreCase("sixty")) {
//                        result += 60;
//                    } else if (str.equalsIgnoreCase("seventy")) {
//                        result += 70;
//                    } else if (str.equalsIgnoreCase("eighty")) {
//                        result += 80;
//                    } else if (str.equalsIgnoreCase("ninety")) {
//                        result += 90;
//                    } else if (str.equalsIgnoreCase("hundred")) {
//                        result *= 100;
//                    } else if (str.equalsIgnoreCase("thousand")) {
//                        result *= 1000;
//                        finalResult += result;
//                        result = 0;
//                    } else if (str.equalsIgnoreCase("million")) {
//                        result *= 1000000;
//                        finalResult += result;
//                        result = 0;
//                    } else if (str.equalsIgnoreCase("billion")) {
//                        result *= 1000000000;
//                        finalResult += result;
//                        result = 0;
//                    } else if (str.equalsIgnoreCase("trillion")) {
//                        result *= 1000000000000L;
//                        finalResult += result;
//                        result = 0;
//                    }
//                }
//
//                finalResult += result;
//                result = 0;
//                System.out.println("FINAL RESULT: " + finalResult);
//            }
//        }
//        return finalResult;
//    }
}