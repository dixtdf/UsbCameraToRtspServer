package com.example.usbcameratortspserver;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.herohan.uvcapp.CameraException;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.pedro.common.ConnectCheckerEvent;
import com.pedro.common.StreamEvent;
import com.pedro.encoder.utils.gl.AspectRatioMode;
import com.pedro.library.util.sources.audio.MicrophoneSource;
import com.pedro.library.util.sources.video.VideoSource;
import com.pedro.library.view.GlStreamInterface;
import com.pedro.library.view.OrientationForced;
import com.pedro.rtspserver.RtspServerStream;

import org.apache.commons.lang3.StringUtils;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Context mContext = this;

        // Here we need new CameraHelper. CameraHelper try to make sure it is globally unique, otherwise there may be usb occupancy issues
        CameraHelper usbCameraHelper = new CameraHelper();
        usbCameraHelper.setStateCallback(new ICameraHelper.StateCallback() {
            @Override
            public void onAttach(UsbDevice device) {
                try {
                    // The main thing here is to get the permissions for the usb device
                    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

                    PendingIntent permissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent("com.example.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);
                    usbManager.requestPermission(device, permissionIntent);
                    usbCameraHelper.selectDevice(device);
                } catch (Exception e) {
                    Log.e(TAG, "openCamera error:" + e.getMessage());
                }
            }

            @Override
            public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
                try {
                    //Turn on the camera here.
                    usbCameraHelper.openCamera();
                } catch (Exception e) {
                    Log.e(TAG, "openCamera error:" + e.getMessage());
                }
            }

            @Override
            public void onCameraOpen(UsbDevice device) {
                try {
                    usbCameraHelper.startPreview();
                    String deviceName = StringUtils.substring(device.getDeviceName(), StringUtils.lastIndexOf(device.getDeviceName(), "/") + 1);
                    //Define an rtsp port we want here
                    //The rtsp playback address is rtsp://localip:port
                    int port = 10554 + Integer.valueOf(deviceName);

                    // 在这里我使用了自定义输入源，值得注意的是isRunning在第一次初始化是不能为true,否则不能正确进入start,我们需要给start的SurfaceTexture进行渲染,所以我给了false,如果SurfaceTexture画面没有正确渲染,RtspServerStream也就没有意义
                    // Here I used a custom input source, it is worth noting that isRunning in the first initialization can not be true, otherwise it can not be correctly into the start, we need to give the start of the SurfaceTexture rendering, so I gave false, if the SurfaceTexture screen is not correctly rendered, the RtspServerStream is also meaningless.
                    RtspServerStream rtspServerStream = new RtspServerStream(mContext, port, new ConnectCheckerEvent() {
                        @Override
                        public void onStreamEvent(StreamEvent streamEvent, String s) {
                            // Some initialization and connection information is responded to here
                            Log.d(TAG, s);
                        }
                    }, new VideoSource() {
                        @Override
                        protected boolean create(int i, int i1, int i2, int i3) {
                            return true;
                        }

                        @Override
                        public void start(@NonNull SurfaceTexture surfaceTexture) {
                            usbCameraHelper.addSurface(surfaceTexture, false);
                        }

                        @Override
                        public void stop() {

                        }

                        @Override
                        public void release() {

                        }

                        @Override
                        public boolean isRunning() {
                            return false;
                        }
                    }, new MicrophoneSource());
                    // 这里我设置了一些输出流的配置,比如setAspectRatioMode和forceOrientation,如果你的画面始终竖向可以尝试修改它们
                    // Here I've set up some of the output stream configurations, such as setAspectRatioMode and forceOrientation, and if your screen is always vertical, you can try to change them
                    GlStreamInterface glInterface = rtspServerStream.getGlInterface();
                    glInterface.setAspectRatioMode(AspectRatioMode.Adjust);
                    glInterface.forceOrientation(OrientationForced.LANDSCAPE);

                    boolean prepareVideo = rtspServerStream.prepareVideo(1000, 1000, 6000 * 1024, 25, 0, 90);
                    boolean prepareAudio = rtspServerStream.prepareAudio(48000, false, 128000);
                    if (prepareVideo && prepareAudio) {
                        rtspServerStream.startStream();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onCameraOpen error:" + e.getMessage());
                }
            }

            @Override
            public void onCameraClose(UsbDevice device) {

            }

            @Override
            public void onDeviceClose(UsbDevice device) {
            }

            @Override
            public void onDetach(UsbDevice device) {
            }

            @Override
            public void onCancel(UsbDevice device) {
            }

            @Override
            public void onError(UsbDevice device, CameraException e) {
            }
        });


    }
}