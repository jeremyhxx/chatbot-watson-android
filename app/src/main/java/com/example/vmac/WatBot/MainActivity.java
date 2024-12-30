package com.example.vmac.WatBot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.content.ActivityNotFoundException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import android.media.MediaPlayer;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.SessionResponse;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import com.ibm.watson.assistant.v2.model.DialogNodeOutputOptionsElement;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;

public class MainActivity extends AppCompatActivity implements android.speech.tts.TextToSpeech.OnInitListener {

    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList<Message> messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;
    private MediaPlayer mediaPlayer;
    private boolean isRecording = false;
    private boolean initialRequest;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String TAG = "MainActivity";
    private static final int SPEECH_REQUEST_CODE = 101;

    private Assistant watsonAssistant;
    private Response<SessionResponse> watsonAssistantSession;
    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady = false;

    private Context mContext;

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28", new IamAuthenticator(getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(getString(R.string.assistant_url));
        
        // Initialize Android TTS
        tts = new android.speech.tts.TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || 
                result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
                Toast.makeText(this, "Text to speech language not supported", Toast.LENGTH_SHORT).show();
            } else {
                ttsReady = true;
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);
            }
        } else {
            Log.e(TAG, "TTS Initialization failed");
            Toast.makeText(this, "Text to speech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        inputMessage = findViewById(R.id.message);
        btnSend = findViewById(R.id.btn_send);
        btnRecord = findViewById(R.id.btn_record);
        String customFont = "Montserrat-Regular.ttf";
        Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
        inputMessage.setTypeface(typeface);
        recyclerView = findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;

        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        } else {
            Log.i(TAG, "Permission to record was already granted");
        }

        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), recyclerView,
                new ClickListener() {
                    @Override
                    public void onClick(View view, final int position) {
                        Message audioMessage = messageArrayList.get(position);
                        if (audioMessage != null && !audioMessage.getMessage().isEmpty()) {
                            new SayTask().execute(audioMessage.getMessage());
                        }
                    }

                    @Override
                    public void onLongClick(View view, int position) {
                        recordMessage();
                    }
                }));

        btnSend.setOnClickListener(v -> {
            if (checkInternetConnection()) {
                sendMessage();
            }
        });

        btnRecord.setOnClickListener(v -> recordMessage());

        createServices();
        sendMessage();
    }

    private void recordMessage() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition is not supported on this device", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                inputMessage.setText(spokenText);
                sendMessage();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            finish();
            startActivity(getIntent());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission granted");
                } else {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    private void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private void sendMessage() {
        final String inputmessage = this.inputMessage.getText().toString().trim();
        
        // Clear the input field first
        this.inputMessage.setText("");

        // Only add non-empty messages to UI
        if (!inputmessage.isEmpty()) {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("1");
            inputMessage.setType(Message.Type.TEXT);
            messageArrayList.add(inputMessage);
            mAdapter.notifyDataSetChanged();
            
            // Scroll to bottom only if we have items
            if (mAdapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(mAdapter.getItemCount() - 1);
            }
        }

        if (this.initialRequest) {
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();
        }

        // Send to Watson Assistant
        Thread thread = new Thread(() -> {
            try {
                if (watsonAssistantSession == null) {
                    ServiceCall<SessionResponse> call = watsonAssistant.createSession(new CreateSessionOptions.Builder()
                            .assistantId(getString(R.string.assistant_id))
                            .build());
                    watsonAssistantSession = call.execute();
                }

                MessageInput input = new MessageInput.Builder()
                        .text(inputmessage)
                        .build();
                MessageOptions options = new MessageOptions.Builder()
                        .assistantId(getString(R.string.assistant_id))
                        .input(input)
                        .sessionId(watsonAssistantSession.getResult().getSessionId())
                        .build();
                Response<MessageResponse> response = watsonAssistant.message(options).execute();
                Log.i(TAG, "run: " + response.getResult());

                if (response != null && response.getResult().getOutput() != null) {
                    // Process response messages
                    if (!response.getResult().getOutput().getGeneric().isEmpty()) {
                        List<RuntimeResponseGeneric> responses = response.getResult().getOutput().getGeneric();
                        for (RuntimeResponseGeneric r : responses) {
                            Message outMessage;
                            switch (r.responseType()) {
                                case "text":
                                    outMessage = new Message();
                                    outMessage.setMessage(r.text());
                                    outMessage.setId("2");
                                    outMessage.setType(Message.Type.TEXT);
                                    messageArrayList.add(outMessage);
                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "option":
                                    outMessage = new Message();
                                    String title = r.title();
                                    StringBuilder optionsOutput = new StringBuilder();
                                    for (DialogNodeOutputOptionsElement option : r.options()) {
                                        optionsOutput.append(option.getLabel()).append("\n");
                                    }
                                    outMessage.setMessage(title + "\n" + optionsOutput.toString());
                                    outMessage.setId("2");
                                    outMessage.setType(Message.Type.TEXT);
                                    messageArrayList.add(outMessage);
                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "image":
                                    outMessage = new Message(r);
                                    messageArrayList.add(outMessage);
                                    // speak the description
                                    new SayTask().execute("You received an image: " + outMessage.getTitle() + outMessage.getDescription());
                                    break;

                                default:
                                    Log.e("Error", "Unhandled message type");
                            }
                        }

                        runOnUiThread(() -> {
                            mAdapter.notifyDataSetChanged();
                            // Only scroll if we have items
                            if (mAdapter.getItemCount() > 0) {
                                recyclerView.smoothScrollToPosition(mAdapter.getItemCount() - 1);
                            }
                        });
                    } else {
                        // If we received a response with empty generic array, send an empty message
                        try {
                            Thread.sleep(100);  // Small delay
                            runOnUiThread(() -> sendMessage());
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Error in delay before sending empty message", e);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread.start();
    }

    private boolean checkInternetConnection() {
        // Check internet connectivity
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if (!isConnected) {
            Toast.makeText(this, "Please check your internet connection", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private class SayTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            return params[0];
        }

        @Override
        protected void onPostExecute(String text) {
            if (ttsReady) {
                tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "MessageId_" + System.currentTimeMillis());
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        // Clean up Watson services
        if (watsonAssistantSession != null) {
            try {
                watsonAssistant.deleteSession(new com.ibm.watson.assistant.v2.model.DeleteSessionOptions.Builder()
                    .assistantId(getString(R.string.assistant_id))
                    .sessionId(watsonAssistantSession.getResult().getSessionId())
                    .build()).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete Watson Assistant session", e);
            }
        }
        super.onDestroy();
    }
}



