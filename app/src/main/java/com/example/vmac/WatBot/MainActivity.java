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

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList<Message> messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private boolean isRecording = false;
    private boolean initialRequest;
    private boolean permissionToRecordAccepted = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String TAG = "MainActivity";
    private static final int RECORD_REQUEST_CODE = 101;
    private boolean listening = false;

    private Assistant watsonAssistant;
    private Response<SessionResponse> watsonAssistantSession;
    private SpeechToText speechService;
    private TextToSpeech textToSpeech;

    private Context mContext;

    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT;
    private android.media.AudioRecord audioRecord;
    private Thread recordingThread = null;

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28", new IamAuthenticator(getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(getString(R.string.assistant_url));

        textToSpeech = new TextToSpeech(new IamAuthenticator(getString(R.string.TTS_apikey)));
        textToSpeech.setServiceUrl(getString(R.string.TTS_url));

        speechService = new SpeechToText(new IamAuthenticator(getString(R.string.STT_apikey)));
        speechService.setServiceUrl(getString(R.string.STT_url));
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

        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/recording.wav";

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
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (!permissionToRecordAccepted) {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case RECORD_REQUEST_CODE:
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                }
                break;
        }
    }

    protected void makeRequest() {
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

    //Record a message via Watson Speech to Text
    private void recordMessage() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            int minBufferSize = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            audioRecord = new android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize
            );

            File audioFile = new File(getCacheDir(), "recording.pcm");
            audioFilePath = audioFile.getAbsolutePath();

            audioRecord.startRecording();
            isRecording = true;
            btnRecord.setImageResource(R.drawable.ic_stop);
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[minBufferSize];
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(audioFile)) {
                    while (isRecording) {
                        int read = audioRecord.read(buffer, 0, minBufferSize);
                        if (read > 0) {
                            fos.write(buffer, 0, read);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing audio data", e);
                }
            });
            recordingThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            releaseAudioRecord();
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (audioRecord != null && isRecording) {
            try {
                isRecording = false;
                if (recordingThread != null) {
                    recordingThread.join();
                    recordingThread = null;
                }
                audioRecord.stop();
                btnRecord.setImageResource(R.drawable.ic_mic);
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                
                // Convert audio to text
                convertAudioToText();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop recording", e);
                Toast.makeText(this, "Failed to stop recording", Toast.LENGTH_SHORT).show();
            } finally {
                releaseAudioRecord();
            }
        }
    }

    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
    }

    private void convertAudioToText() {
        try {
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show();
                return;
            }

            FileInputStream fis = new FileInputStream(audioFile);
            RecognizeOptions recognizeOptions = new RecognizeOptions.Builder()
                    .audio(fis)
                    .contentType("audio/l16;rate=8000;channels=1;endianness=little-endian")
                    .model("en-US_NarrowbandModel")
                    .inactivityTimeout(60)
                    .interimResults(false)  // Only get final results
                    .build();

            BaseRecognizeCallback callback = new BaseRecognizeCallback() {
                private boolean hasProcessedResult = false;  // Flag to track if we've processed a result

                @Override
                public void onTranscription(SpeechRecognitionResults speechResults) {
                    if (speechResults.getResults() != null && 
                        !speechResults.getResults().isEmpty() && 
                        !hasProcessedResult) {  // Only process if we haven't already
                        
                        hasProcessedResult = true;  // Mark as processed
                        String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                        runOnUiThread(() -> {
                            inputMessage.setText(text);
                            sendMessage();
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Error during speech recognition", e);
                        Toast.makeText(MainActivity.this, "Failed to convert speech to text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        audioFile.delete();
                    });
                }
            };

            speechService.recognizeUsingWebSocket(recognizeOptions, callback);

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert audio to text", e);
            Toast.makeText(this, "Failed to convert audio to text", Toast.LENGTH_SHORT).show();
        }
    }

    private class SayTask extends AsyncTask<String, Void, String> {
        private File tempFile = null;

        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            SynthesizeOptions synthesizeOptions = new SynthesizeOptions.Builder()
                    .text(text)
                    .accept("audio/mp3")
                    .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)
                    .build();

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

                // Get the audio stream from Watson
                InputStream audioStream = textToSpeech.synthesize(synthesizeOptions).execute().getResult();
                
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
                    throw new IOException("Failed to write audio file");
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
                        mediaPlayer.setOnCompletionListener(mp -> {
                            cleanupMediaPlayer();
                        });

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
                        Log.e(TAG, "Failed to set up MediaPlayer", e);
                        cleanupMediaPlayer();
                    }
                });
                
                return "Did synthesize";
            } catch (IOException e) {
                Log.e(TAG, "Failed to synthesize audio", e);
                cleanupMediaPlayer();
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
            if (result.equals("Failed to synthesize")) {
                Toast.makeText(MainActivity.this, "Failed to play audio", Toast.LENGTH_SHORT).show();
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
        releaseAudioRecord();
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



