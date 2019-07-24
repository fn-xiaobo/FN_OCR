package com.fn.app4;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.google.android.cameraview.CameraImpl;

import me.pqpo.smartcameralib.SmartCameraView;

public class MainActivity extends AppCompatActivity {

    private SmartCameraView mSmartCameraView;
    private ImageView mIv;
    Bitmap bitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIv = (ImageView) findViewById(R.id.iv);

        mSmartCameraView = (SmartCameraView) findViewById(R.id.camera_view);
        mSmartCameraView.getSmartScanner().setPreview(true);
        mSmartCameraView.setOnScanResultListener(new SmartCameraView.OnScanResultListener() {
            @Override
            public boolean onScanResult(SmartCameraView smartCameraView, int i, byte[] result) {
//                Bitmap previewBitmap = smartCameraView.getPreviewBitmap();
//                if (previewBitmap != null) {
//                    mIv.setImageBitmap(previewBitmap);
//                }
                return false;
            }

        });

        mSmartCameraView.addCallback(new CameraImpl.Callback() {
            @Override
            public void onCameraOpened(CameraImpl camera) {
                super.onCameraOpened(camera);
            }

            @Override
            public void onCameraClosed(CameraImpl camera) {
                super.onCameraClosed(camera);
            }

            @Override
            public void onPictureTaken(CameraImpl camera, byte[] data) {
                super.onPictureTaken(camera, data);
            }

            @Override
            public void onPicturePreview(CameraImpl camera, final byte[] data) {
                super.onPicturePreview(camera, data);

                Log.i("fn_tag", "实时预览中");
                new Thread() {
                    @Override
                    public void run() {
                        super.run();

                        bitmap = cameraByte2Bitmap(data, 500, 500);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if (MainActivity.this.bitmap != null) {

                                    mIv.setImageBitmap(MainActivity.this.bitmap);
                                }

                            }
                        });
                    }
                }.start();
//

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSmartCameraView.start();
        mSmartCameraView.startScan();
    }


    @Override
    protected void onPause() {
        mSmartCameraView.stop();
        super.onPause();

        mSmartCameraView.stopScan();
    }

    //视频帧转位图  YuvImage 也可用于转换，但此类并不兼容低版本设备！
    public Bitmap cameraByte2Bitmap(byte[] data, int width, int height) {

        int frameSize = width * height;
        int[] rgba = new int[frameSize];

        for (int i = 0; i < height; i++)

            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                y = y < 16 ? 16 : y;
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bmp.setPixels(rgba, 0, width, 0, 0, width, height);

        return bmp;
    }


}
