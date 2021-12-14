package com.example.projectjarvis;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.ArrayList;

public class Controller {

    Link linkObject = new Link();
    TextToSpeech textToSpeech;
    Context context;

    public Controller(TextToSpeech textToSpeech, Context context) {
        this.textToSpeech = textToSpeech; //TODO: Remove this and initialize here with the new Context
        this.context = context;
    }

    public void decodeInput(ArrayList<String> result) {

        //TODO: skulle kunna skapa en samling med fraser som är okej? Ev göra det i en egen klass eller typ JSON?
        String resultString = result.toString().toLowerCase(); //TODO: Move this into onActivityResult ist? Snyggare för användaren
        if (resultString.contains("turn on the lamp")) {

            feedback("Turning on the lamp");
        } else if (resultString.contains(("turn off the lamp"))) {

            feedback("Turning off the lamp");
        } else if (resultString.contains(("turn on the christmas tree"))) {

            feedback("Turning on the christmas tree");
        } else if (resultString.contains(("turn off the christmas tree"))) {

            feedback("Turning off the christmas tree");
        } else {
            feedback("No valid input, please try again!");
        }
    }

    private void feedback(String string) {
        System.out.println(string);
        textToSpeech.speak(string, TextToSpeech.QUEUE_FLUSH, null);
        Toast toast = Toast.makeText(context, string, Toast.LENGTH_SHORT);
        toast.show();
    }
}
