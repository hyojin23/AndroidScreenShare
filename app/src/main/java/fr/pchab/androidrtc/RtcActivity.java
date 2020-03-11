
package fr.pchab.androidrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;

import static android.content.ContentValues.TAG;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener {
    private WebRtcClient mWebRtcClient;
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;
    //    private EglBase rootEglBase;
    private static Intent mMediaProjectionPermissionResultData;
    private static int mMediaProjectionPermissionResultCode;

    public static String STREAM_NAME_PREFIX = "android_device_stream";
    // List of mandatory application permissions.／
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    //    private SurfaceViewRenderer pipRenderer;
//    private SurfaceViewRenderer fullscreenRenderer;
    public static int sDeviceWidth;
    public static int sDeviceHeight;
    public static final int SCREEN_RESOLUTION_SCALE = 2;
    Button stop_btn;
    Button start_btn;
    ScreenCapturerAndroid screenCapturerAndroid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_rtc);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        sDeviceWidth = metrics.widthPixels;
        sDeviceHeight = metrics.heightPixels;

        // 시작 버튼
        start_btn = findViewById((R.id.start_btn));
        // 가운데 버튼
        stop_btn = findViewById(R.id.stop_btn);

        // 시작 버튼 클릭
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 녹화 권한 요청
                startScreenCapture();
            }
        });
        // 중지 버튼 클릭
        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "화면 녹화 중지", Toast.LENGTH_SHORT).show();
                // 녹화 중지
                if (screenCapturerAndroid != null) {
                    screenCapturerAndroid.stopCapture();
                }
                // 소켓 연결 해제
                if (mWebRtcClient != null) {
                    mWebRtcClient.destroy();
                }
            }
        });

//        pipRenderer = (SurfaceViewRenderer) findViewById(R.id.pip_video_view);
//        fullscreenRenderer = (SurfaceViewRenderer) findViewById(R.id.fullscreen_video_view);

//        EglBase rootEglBase = EglBase.create();
//        pipRenderer.init(rootEglBase.getEglBaseContext(), null);
//        pipRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
//        fullscreenRenderer.init(rootEglBase.getEglBaseContext(), null);
//        fullscreenRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

//        pipRenderer.setZOrderMediaOverlay(true);
//        pipRenderer.setEnableHardwareScaler(true /* enabled */);
//        fullscreenRenderer.setEnableHardwareScaler(true /* enabled */);

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        // 현재 기기의 SDK 버전이 롤리팝(SDK 21)보다 높으면
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            startScreenCapture();
        } else {
            init();
        }

    }



    // 화면 녹화 권한을 요청
    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }

    @TargetApi(21)
    private VideoCapturer createScreenCapturer() {
        Log.d(TAG, "createScreenCapturer: 실행");
        // 유저가 취소 버튼을 눌렀을 경우
        if (mMediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            report("User didn't give permission to capture the screen.");
            return null;
        }
//        return new ScreenCapturerAndroid(
//                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
//            @Override
//            public void onStop() {
//                report("User revoked permission to capture the screen.");
//            }
//        });
        screenCapturerAndroid = new ScreenCapturerAndroid(
                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                report("User revoked permission to capture the screen.");
            }
        });
        return screenCapturerAndroid;
    }


    // 녹화 권한 요청에 사용자가 응답한 후 호출됨 (취소 resultCode: 0, 시작하기 resultCode: -1)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Toast.makeText(getApplicationContext(), "onActivityResult 실행", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onActivityResult: resultCode: " +resultCode);
        Log.d(TAG, "onActivityResult: requestCode: " + requestCode);
        Log.d(TAG, "onActivityResult: data: " + data);
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        mMediaProjectionPermissionResultCode = resultCode;
        mMediaProjectionPermissionResultData = data;
        init();
    }

    private void init() {
        Log.d(TAG, "init: 실행");
        if (createScreenCapturer() != null) {
            PeerConnectionClient.PeerConnectionParameters peerConnectionParameters =
                    new PeerConnectionClient.PeerConnectionParameters(true, false,
                            true, sDeviceWidth / SCREEN_RESOLUTION_SCALE, sDeviceHeight / SCREEN_RESOLUTION_SCALE, 0,
                            0, "VP8",
                            false,
                            true,
                            0,
                            "OPUS", false, false, false, false, false, false, false, false, null);
//        mWebRtcClient = new WebRtcClient(getApplicationContext(), this, pipRenderer, fullscreenRenderer, createScreenCapturer(), peerConnectionParameters);
            mWebRtcClient = new WebRtcClient(getApplicationContext(), this, createScreenCapturer(), peerConnectionParameters);
        }
    }

    public void report(String info) {
        Log.e(TAG, info);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        if (mWebRtcClient != null) {
//            mWebRtcClient.onDestroy();
        }
        super.onDestroy();
    }

    // 시작하기를 눌러 화면 공유가 시작되면 호출되는 콜백 메소드
    @Override
    public void onReady(String callId) {
        Log.d(TAG, "onReady: callId: " + callId);
        Log.d(TAG, "onReady: 실행");
        mWebRtcClient.start(STREAM_NAME_PREFIX);
    }


    @Override
    public void onCall(final String applicant) {
        Log.d(TAG, "onCall: 실행");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    @Override
    public void onHandup() {

    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
