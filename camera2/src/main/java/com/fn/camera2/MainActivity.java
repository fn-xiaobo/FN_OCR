package com.fn.camera2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 开发参考链接:  https://blog.csdn.net/lb377463323/article/details/52740411
 * 预览实现:  https://blog.csdn.net/qq_21898059/article/details/50986290  https://github.com/plumcot/Camera2Demo
 */
public class MainActivity extends AppCompatActivity {


    private static final String TAG = "MainActivity";
    private TextureView mTextureView;
    private Handler mHandler;
    private HandlerThread mThreadHandler;
    private Size mPreviewSize;
    private CaptureRequest.Builder mPreviewBuilder;

    private CameraDevice mCamera;

    // 这里定义的是ImageReader回调的图片的大小
    private int mImageWidth = 1920;
    private int mImageHeight = 1080;
    private ImageReader mImageReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        initView();
        initLooper();

    }

    private void initLooper() {

        mThreadHandler = new HandlerThread("CAMERA2");
        mThreadHandler.start();
        mHandler = new Handler(mThreadHandler.getLooper());

    }

    private void initView() {

        mTextureView = (TextureView) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mTextureView.isAvailable()) {

            mTextureView.setSurfaceTextureListener(mTextureListener);

        } else {

        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mCamera) {
            mCamera.close();
            mCamera = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }


    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            try {
                //获得所有摄像头的管理者CameraManager
                CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                //获得某个摄像头的特征，支持的参数
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
                //支持的STREAM CONFIGURATION
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //摄像头支持的预览Size数组
                mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];
                //打开相机
                cameraManager.openCamera("0", mCameraDeviceStateCallback, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        // 这个方法要注意一下，因为每有一帧画面，都会回调一次此方法
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };


    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            try {
                mCamera = camera;
                startPreview(mCamera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    };

    // 开始预览，主要是camera.createCaptureSession这段代码很重要，创建会话
    private void startPreview(CameraDevice camera) throws CameraAccessException {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();

//      这里设置的就是预览大小
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        try {
            // 设置捕获请求为预览，这里还有拍照啊，录像等
            mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

//      就是在这里，通过这个set(key,value)方法，设置曝光啊，自动聚焦等参数！！ 如下举例：
//      mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

        mImageReader = ImageReader.newInstance(mImageWidth, mImageHeight, ImageFormat.JPEG/*此处还有很多格式，比如我所用到YUV等*/, 2/*最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张*/);

        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);
        // 这里一定分别add两个surface，一个Textureview的，一个ImageReader的，如果没add，会造成没摄像头预览，或者没有ImageReader的那个回调！！
        mPreviewBuilder.addTarget(surface);
        mPreviewBuilder.addTarget(mImageReader.getSurface());
        camera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mHandler);
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                updatePreview(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    private void updatePreview(CameraCaptureSession session) throws CameraAccessException {
        session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {


        /**
         *  当有一张图片可用时会回调此方法，但有一点一定要注意：
         *  一定要调用 reader.acquireNextImage()和close()方法，否则画面就会卡住！！！！！我被这个坑坑了好久！！！
         *    很多人可能写Demo就在这里打一个Log，结果卡住了，或者方法不能一直被回调。
         **/
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireNextImage();
            /**
             *  因为Camera2并没有Camera1的Priview回调！！！所以该怎么能到预览图像的byte[]呢？就是在这里了！！！我找了好久的办法！！！
             **/
            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            img.close();

            Log.d(TAG, "onImageAvailable() called with: reader = [" + reader + "]");
        }
    };


}
