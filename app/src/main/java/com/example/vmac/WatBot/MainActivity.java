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

import com.ibm.cloud.sdk.core.http.HttpMediaType;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.model.DialogNodeOutputOptionsElement;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.SessionResponse;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.Locale;
import android.content.ActivityNotFoundException;

public class MainActivity extends AppCompatActivity {

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
    private TextToSpeech textToSpeech;

    private Context mContext;

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28", new IamAuthenticator(getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(getString(R.string.assistant_url));

        textToSpeech = new TextToSpeech(new IamAuthenticator(getString(R.string.TTS_apikey)));
        textToSpeech.setServiceUrl(getString(R.string.TTS_url));
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
        private File tempFile = null;

        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            try {
                // Clean up any existing MediaPlayer first
                if (mediaPlayer != null) {
                    runOnUiThread(() -> {
                        try {
                            mediaPlayer.stop();
                        } catch (IllegalStateException e) {
                            // Ignore state errors
                        }
                        mediaPlayer.release();
                        mediaPlayer = null;
                    });
                }

                // Create Text-to-Speech request
                SynthesizeOptions synthesizeOptions = new SynthesizeOptions.Builder()
                        .text(text)
                        .accept("audio/mp3")
                        .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)  // Try a different voice
                        .build();

                // Get the audio stream from Watson
                Response<InputStream> ttsResponse = textToSpeech.synthesize(synthesizeOptions).execute();
                if (ttsResponse == null || ttsResponse.getResult() == null) {
                    Log.e(TAG, "Text-to-Speech response or result is null");
                    return "Failed to synthesize";
                }

                InputStream audioStream = ttsResponse.getResult();
                
                // Write the audio stream to a temporary file
                tempFile = File.createTempFile("tts_", ".mp3", getCacheDir());
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = audioStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.flush();
                }
                audioStream.close();

                // Ensure the file exists and has content
                if (!tempFile.exists() || tempFile.length() == 0) {
                    Log.e(TAG, "Failed to write audio file or file is empty");
                    return "Failed to synthesize";
                }

                // Create and set up new MediaPlayer on UI thread
                runOnUiThread(() -> {
                    try {
                        mediaPlayer = new MediaPlayer();
                        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                            cleanupMediaPlayer();
                            return true;
                        });

                        // Set up completion listener before preparing
                        mediaPlayer.setOnCompletionListener(mp -> cleanupMediaPlayer());

                        mediaPlayer.setDataSource(tempFile.getAbsolutePath());
                        mediaPlayer.setOnPreparedListener(mp -> {
                            try {
                                mp.start();
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Failed to start MediaPlayer", e);
                                cleanupMediaPlayer();
                            }
                        });
                        
                        // Prepare asynchronously
                        mediaPlayer.prepareAsync();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to set up MediaPlayer: " + e.getMessage(), e);
                        cleanupMediaPlayer();
                    }
                });
                
                return "Did synthesize";
            } catch (com.ibm.cloud.sdk.core.service.exception.ForbiddenException e) {
                Log.e(TAG, "Text-to-Speech service authentication failed: " + e.getMessage(), e);
                return "Authentication failed";
            } catch (Exception e) {
                Log.e(TAG, "Failed to synthesize audio: " + e.getMessage(), e);
                return "Failed to synthesize";
            }
        }

        private void cleanupMediaPlayer() {
            runOnUiThread(() -> {
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.stop();
                    } catch (IllegalStateException e) {
                        // Ignore state errors
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                if (tempFile != null) {
                    tempFile.delete();
                    tempFile = null;
                }
            });
        }

        @Override
        protected void onPostExecute(String result) {
            switch (result) {
                case "Authentication failed":
                    Toast.makeText(MainActivity.this, "Text-to-Speech service authentication failed. Please check your credentials.", Toast.LENGTH_LONG).show();
                    break;
                case "Failed to synthesize":
                    Toast.makeText(MainActivity.this, "Failed to play audio", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
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
    }
}



