package com.fn.app3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

public class Camera2Activity extends AppCompatActivity implements View.OnClickListener, EasyPermissions.PermissionCallbacks{

    // ======================= 权限部分 start ========================
    private static final int REQUEST_VIDEO_PERMISSIONS = 0;
    private static final String[] VIDEO_PERMISSIONS = {

            Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA

    };
    private static final String TAG = "Camera2Activity";
    private CameraPreview cameraView;

    // ======================= 权限部分 end ========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        initView();
    }


    private void initView() {
        cameraView = (CameraPreview) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume(this);
    }

    @Override
    protected void onPause() {
        cameraView.onPause();
        super.onPause();
    }


    @Override
    public void onClick(View v) {

    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }
}
