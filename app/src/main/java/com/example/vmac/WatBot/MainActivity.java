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
        if (!this.initialRequest) {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("1");
            inputMessage.setType(Message.Type.TEXT);
            messageArrayList.add(inputMessage);
        } else {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("100");
            inputMessage.setType(Message.Type.TEXT);
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();
        }

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

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
                if (response != null &&
                        response.getResult().getOutput() != null &&
                        !response.getResult().getOutput().getGeneric().isEmpty()) {

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
                        if (mAdapter.getItemCount() > 1) {
                            recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView,
                                    null, mAdapter.getItemCount() - 1);
                        }
                    });
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
            if (mediaRecorder != null) {
                mediaRecorder.release();
            }
            File audioFile = new File(getCacheDir(), "recording.wav");
            audioFilePath = audioFile.getAbsolutePath();
            
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFilePath);
            
            // Prepare under try-catch to handle prepare failures gracefully
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "Failed to prepare MediaRecorder", e);
                releaseMediaRecorder();
                Toast.makeText(this, "Failed to prepare recording", Toast.LENGTH_SHORT).show();
                return;
            }
            
            mediaRecorder.start();
            isRecording = true;
            btnRecord.setImageResource(R.drawable.ic_stop); // Assuming you have stop icon
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            releaseMediaRecorder();
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                isRecording = false;
                btnRecord.setImageResource(R.drawable.ic_mic); // Assuming you have mic icon
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                
                // Convert audio to text
                convertAudioToText();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop recording", e);
                Toast.makeText(this, "Failed to stop recording", Toast.LENGTH_SHORT).show();
            } finally {
                releaseMediaRecorder();
            }
        }
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            mediaRecorder = null;
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
                    .contentType("audio/mp4")
                    .build();

            BaseRecognizeCallback callback = new BaseRecognizeCallback() {
                @Override
                public void onTranscription(SpeechRecognitionResults speechResults) {
                    if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
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
                        Toast.makeText(MainActivity.this, "Failed to convert speech to text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        audioFile.delete(); // Clean up the audio file
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
        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            SynthesizeOptions synthesizeOptions = new SynthesizeOptions.Builder()
                    .text(text)
                    .accept(HttpMediaType.AUDIO_WAV)
                    .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)
                    .build();

            try {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }
                mediaPlayer = new MediaPlayer();
                InputStream audioStream = textToSpeech.synthesize(synthesizeOptions).execute().getResult();
                
                // Write the audio stream to a temporary file
                File tempFile = File.createTempFile("tts_", ".wav", getCacheDir());
                java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = audioStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                audioStream.close();
                
                mediaPlayer.setDataSource(tempFile.getPath());
                mediaPlayer.prepare();
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.reset();
                    tempFile.delete();
                });
                mediaPlayer.start();
            } catch (IOException e) {
                Log.e(TAG, "Failed to play audio", e);
            }
            return "Did synthesize";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
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



