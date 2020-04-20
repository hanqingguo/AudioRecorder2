package com.example.audiorecord;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audiorecord.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private Button mBtnStartRecordAudio;
    private Button mBtnStopRecordAudio;
    private Button mBtnStartPlayAudio;
    private Button mBtnStopPlayAudio;

    private String mAudioFilePath;

    private boolean mIsRecording; // 控制录制音频标志
    private AudioRecordTask mAudioRecordTask; // 异步录制音频任务
    private boolean mIsPlaying; // 控制播放音频标志
    private AudioPlayTask mAudioPlayTask; // 异步播放音频任务

    private TextView mTvRecordProcess;

    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvRecordProcess = findViewById(R.id.tv_audio_record_process);

        mBtnStartRecordAudio = findViewById(R.id.btn_start_record_audio);
        mBtnStopRecordAudio = findViewById(R.id.btn_stop_record_audio);
        mBtnStartPlayAudio = findViewById(R.id.btn_play_audio);
        mBtnStopPlayAudio = findViewById(R.id.btn_stop_audio);

        mBtnStartRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "开始录制音频", Toast.LENGTH_SHORT).show();
                mBtnStartRecordAudio.setEnabled(false);
                mBtnStopRecordAudio.setEnabled(true);

                mAudioRecordTask = new AudioRecordTask();
                mAudioRecordTask.execute();
            }
        });

        mBtnStopRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "停止录制音频", Toast.LENGTH_SHORT).show();
                mBtnStartRecordAudio.setEnabled(true);
                mBtnStopRecordAudio.setEnabled(false);
                mBtnStartPlayAudio.setEnabled(true);

                mIsRecording = false;
            }
        });

        mBtnStartPlayAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "开始播放音频", Toast.LENGTH_SHORT).show();
                mBtnStartRecordAudio.setEnabled(false);
                mBtnStopRecordAudio.setEnabled(false);
                mBtnStartPlayAudio.setEnabled(false);
                mBtnStopPlayAudio.setEnabled(true);

                mAudioPlayTask = new AudioPlayTask();
                mAudioPlayTask.execute();
            }
        });

        mBtnStopPlayAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "停止播放音频", Toast.LENGTH_SHORT).show();
                mBtnStartRecordAudio.setEnabled(true);
                mBtnStopRecordAudio.setEnabled(false);
                mBtnStartPlayAudio.setEnabled(true);
                mBtnStopPlayAudio.setEnabled(false);

                mIsPlaying = false;

                if (new File(mAudioFilePath).delete()) {
                    Log.v(TAG, "audio file delete"); // 1表示删除 0表示未删除
                }
            }
        });

        mBtnStopRecordAudio.setEnabled(false);
        mBtnStartPlayAudio.setEnabled(false);
        mBtnStopPlayAudio.setEnabled(false);
    }

    private boolean createOutputFile() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return false;
        }

        String outputFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/Android/data/com.example.media/audio/";
        File filePath = new File(outputFilePath);
        if (!filePath.exists()) {
            if (filePath.mkdirs()) {
                Log.v(TAG, "audio file path create = " + filePath.getAbsolutePath());
            }
        }
        try {
            File tempAudioFile = File.createTempFile("audio" +
                            new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(new Date()),
                    ".pcm", filePath);
            mAudioFilePath = tempAudioFile.getAbsolutePath();
            return tempAudioFile.exists();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 异步录制原始音频
    @SuppressLint("StaticFieldLeak")
    private class AudioRecordTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            mIsRecording = true;

            int sampleRateInHz = 192000;
            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            //int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRateInHz, channelConfig,
                    audioFormat, bufferSizeInBytes);

            if (createOutputFile()) {
                DataOutputStream dos = null;
                try {
                    dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mAudioFilePath)));

                    short[] buffer = new short[bufferSizeInBytes];
                    audioRecord.startRecording();

                    int r = 0;
                    while (mIsRecording) {
                        int bufferReadResult = audioRecord.read(buffer, 0, bufferSizeInBytes);
                        for (int i = 0; i < bufferReadResult; i++) {
                            dos.writeShort(buffer[i]);
                        }

                        publishProgress(r);
                        r++;
                    }

                    audioRecord.stop();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    closeSilently(dos);
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            String process = values[0].toString();
            mTvRecordProcess.setText(process);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mBtnStartRecordAudio.setEnabled(true);
            mBtnStopRecordAudio.setEnabled(false);
            mBtnStartPlayAudio.setEnabled(true);
        }
    }

    // 异步播放原始音频
    @SuppressLint("StaticFieldLeak")
    private class AudioPlayTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            mIsPlaying = true;

            int sampleRateInHz = 192000;
            int channelConfig = AudioFormat.CHANNEL_IN_FRONT_PROCESSED;
            //int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            short[] buffer = new short[bufferSize / 4];

            DataInputStream dis = null;
            try {
                dis = new DataInputStream(new BufferedInputStream(new FileInputStream(mAudioFilePath)));

                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig,
                        audioFormat, bufferSize, AudioTrack.MODE_STREAM);
                audioTrack.play();

                while (mIsPlaying && dis.available() > 0) {
                    int i = 0;
                    while (dis.available() > 0 && i < buffer.length) {
                        buffer[i] = dis.readShort();
                        i++;
                    }
                    audioTrack.write(buffer, 0, buffer.length);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeSilently(dis);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mBtnStartRecordAudio.setEnabled(true);
            mBtnStopRecordAudio.setEnabled(false);
            mBtnStartPlayAudio.setEnabled(true);
            mBtnStopPlayAudio.setEnabled(false);
        }
    }

    private void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}



