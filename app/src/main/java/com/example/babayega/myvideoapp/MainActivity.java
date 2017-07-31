package com.example.babayega.myvideoapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.ContentValues.TAG;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class MainActivity extends AppCompatActivity{

    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private CameraPreview mPreview;
    private Button playButton;
    private static Context mContext;
    View view;
    private Button startButton;
    private FrameLayout videoFrame;

    public static String VIDEO_PATH = "https://inducesmile.com/wp-content/uploads/2016/05/small.mp4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCamera = getCameraInstance();
        mContext = this;
        playButton = (Button)findViewById(R.id.playButton);
        startButton = (Button)findViewById(R.id.startButton);
        videoFrame = (FrameLayout)findViewById(R.id.videoFrame);
        //view = findViewById(R.id.view);

        //For Getting the Camera to appear in portrait mode
        setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);
        mPreview = new CameraPreview(this, mCamera);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        //Adding the Preview to the FrameLayout
        videoFrame.addView(mPreview);
        //videoFrame.addView(view);

        //Button Listener for Play Button
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent playVideo = new Intent(MainActivity.this, PlayActivity.class);
                playVideo.putExtra("VIDEO_PATH", VIDEO_PATH);
                startActivity(playVideo);

            }
        });

        //Button Listener for Start/Stop Button
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(startButton.getText() == "Stop"){
                    playButton.setVisibility(View.VISIBLE);
                    mMediaRecorder.stop();
                    FFmpeg fFmpeg = FFmpeg.getInstance(getApplicationContext());
                    try {
                        fFmpeg.loadBinary(new LoadBinaryResponseHandler(){
                            @Override
                            public void onStart() {
                                super.onStart();
                            }
                        });
                    } catch (FFmpegNotSupportedException e) {
                        e.printStackTrace();
                    }

                    //Uploading to AWS with ASYNC Task
                    new UploadToAWS().execute();
                    //VIDEO_PATH = Uri.fromFile(getOutputMediaFile(MEDIA_TYPE_VIDEO)).toString();

                    releaseMediaRecorder();

                    startButton.setText("Start");
                }
                else {
                    playButton.setVisibility(View.GONE);
                    if(prepareVideoRecorder()){
                        mMediaRecorder.start();
                        startButton.setText("Stop");
                    }else {
                        releaseMediaRecorder();
                    }
                }
            }
        });
    }

    private class UploadToAWS extends AsyncTask<Void, Void, Void>{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            //Toast toast = Toast.makeText(mContext, "Doing Upload!!", Toast.LENGTH_LONG);
            //toast.show();
            File file = new File(VIDEO_PATH);
            AmazonS3Client amazonS3Client = new AmazonS3Client(new BasicAWSCredentials("AKIAICIFJBFJSWYGTPGA", "Lhow/D0SgGN7DJs++2GJc3Df05ED4HhN6wcUYoGA"));
            TransferUtility utility = new TransferUtility(amazonS3Client, getApplicationContext());
            TransferObserver observer = utility.upload(
                    "shrofilevideotest",
                    "temp",
                    file

            );
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {

                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

                }

                @Override
                public void onError(int id, Exception ex) {
                    Log.e("Error  ",""+ex );
                }
            });
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            //super.onPostExecute(aVoid);
            Toast toast = Toast.makeText(mContext, "Upload Complete!!", Toast.LENGTH_LONG);
            toast.show();
        }
    }


    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private boolean prepareVideoRecorder(){

        mMediaRecorder = new MediaRecorder();

        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));


        mMediaRecorder.setVideoFrameRate(60);


        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        try {
            mMediaRecorder.prepare();
        }
        catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }


    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();
        releaseCamera();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    private void releaseMediaRecorder(){
        if(mMediaRecorder !=null){
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mCamera.lock();
        }
    }

    private void releaseCamera(){
        if(mCamera != null)
        {
            mCamera.release();
            mCamera = null;
        }
    }


    //To Store the File in the Storage with Date Time Stamp
    private static File getOutputMediaFile(int type){

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "VideoApp");

        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("VideoApp", "failed to create directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        VIDEO_PATH = mediaStorageDir.getPath() + File.separator +
                "VID_"+ timeStamp + ".mp4";
        if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(VIDEO_PATH);
        } else {
            return null;
        }

        return mediaFile;

    }


    public static Camera getCameraInstance(){
        Camera c =null;
        try {
            c = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        }
        catch (Exception e){
            Toast toast = Toast.makeText(mContext, "Sorry, your phone does not have a Front camera!", Toast.LENGTH_LONG);
            toast.show();
            e.printStackTrace();
        }
        return c;
    }

}