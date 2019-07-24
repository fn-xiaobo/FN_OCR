package com.fn.camera1;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CustomerCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int PHOTOGRAPH = 1;
    private boolean Enabled = false;
    private int mMax_value;

    private Bitmap mBitmap;

    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private TextView tv1;
    private TextView tv2;
    public static int RESULT_OK = 100;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_customer_camera);
        initView();
    }

    private void initView() {

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);//透明
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);//可以避免一些问题
        mSurfaceHolder.addCallback(this);


        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        try {

                            /**
                             * 点击屏幕对焦
                             */
                            if (mCamera != null) {
                                mCamera.autoFocus(autoFocusCB);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        break;
                }
                return true;
            }

        });


    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {

            mCamera = Camera.open();

            int width = mSurfaceView.getWidth();
            int height = mSurfaceView.getHeight();
            mMax_value = Math.max(width, height);
            Logger.myLog("mSurfaceView -- width:  " + mMax_value);

        } catch (Exception e) {
            // 打开相机异常
            if (null != mCamera) {
                mCamera.release();
                mCamera = null;
            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        //开启相机预览 , 之前先配置一些运行环境
        try {

            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(mSurfaceHolder);

            //todo 关键位置 屏幕适配 如果两个尺寸不同 , 会导致预览变形
            updateCameraParameters(mMax_value);

            //开始预览
            mCamera.startPreview();
            mCamera.cancelAutoFocus();// 只有加上了这一句，才会自动对焦
            mCamera.autoFocus(autoFocusCB);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("fn_tag", "" + e.toString());
        }

    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
    }

    private void stopPreview() {
        //释放资源
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(null);
                mCamera.stopPreview();
            } catch (Exception e) {

            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 自动对焦处理
     */
    private Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {

            if (success) {

                //todo 对焦成功后,验证是否是身份证 , 如果是就去请求服务器获取该用户的其他信息
                Toast.makeText(CustomerCameraActivity.this, "", Toast.LENGTH_SHORT).show();

            }

        }
    };

    Camera.Size previewSize = null;

    private void updateCameraParameters(int width) {

        if (mCamera != null) {

            Camera.Parameters p = mCamera.getParameters();
            p.setPictureFormat(PixelFormat.JPEG);// 设置图片格式
            p.setJpegQuality(100); // 设置照片质量

            Camera.Size picSize = p.getPreviewSize();

            previewSize = getOptimalPreviewSize(p.getSupportedPreviewSizes(), (double) width);

            if (previewSize != null) {

                p.setPreviewSize(previewSize.width, previewSize.height);
            }

            picSize = p.getPictureSize();
            Logger.myLog("PictureSize.width: " + picSize.width + "    PictureSize.height" + picSize.height);
            Camera.Size pictureSize = getOptimalPictureSize(p.getSupportedPictureSizes(), (double) picSize.width / picSize.height);

            if (pictureSize != null) {
                p.setPictureSize(pictureSize.width, pictureSize.height);

            }
            mCamera.setParameters(p);

        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, double width) {

        //匹配分辨率,设定的屏幕的比例不是图片的比例 targetRatio = (double) picSize.width / picSize.height; 预览比率
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {

                //这里实现了降序
                return new Double(lhs.width).compareTo(new Double(rhs.width));//compareTo就是比较两个值，如果前者大于后者，返回1，等于返回0，小于返回-1
            }
        });

        double dMin = 1000.0;
        double dMinDiff = 1000.0;
        Camera.Size RightSize = null;

        for (int i = sizes.size() - 1; i >= 0; i--) {

            Camera.Size size = sizes.get(i);

            if (size.width == size.height) {

                double abs = Math.abs(width - size.width);//取出指定的差值

                //再次遍历到了之后 和上一个差值对比
                if (abs < dMin) {

                    dMin = abs;//临时保存差值
                    RightSize = size;

                }

            }

        }

        optimalSize = RightSize;

        if (optimalSize == null) {

            for (int i = sizes.size() - 1; i >= 0; i--) {

                Camera.Size size = sizes.get(i);

                double abs = Math.abs(width - size.width);//取出指定的差值

//                LogUtils.i("预览的宽高:    width:" + size.width + "     height:" + size.height+"     目标:"+width +"   abs: "+abs);

                //再次遍历到了之后 和上一个差值对比 找到最接近的目标值的尺寸
                if (abs <= dMinDiff) {

                    dMinDiff = abs;
                    RightSize = size;

                    double fRate2 = size.width / (float) size.height;
                    double fDistance2 = Math.abs(fRate2 - 1);

                    //找到比率最接近 1 的 ,
                    if (fDistance2 < dMin) {
                        dMin = fDistance2;

                        RightSize = size;
                    }

                }

            }

            //todo 这里动态的将 surfaceView 的宽高改变
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mSurfaceView.getLayoutParams();
            params.width = RightSize.height;
            params.height = RightSize.width;
            mSurfaceView.setLayoutParams(params);

            optimalSize = RightSize;

        }
//        LogUtils.e("最佳预览的宽高:    width:" + optimalSize.width + "     height:" + optimalSize.height);

        return optimalSize;
    }

    private Camera.Size getOptimalPictureSize(List<Camera.Size> sizes, double targetRatio) {

        //匹配分辨率,设置的是拍照的图片的比例
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;

        Collections.sort(sizes, new Comparator<Camera.Size>() {

            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {

                return new Double(lhs.width).compareTo(new Double(rhs.width));
            }
        });

        for (int i = sizes.size() - 1; i >= 0; i--) {

            Camera.Size size = sizes.get(i);

            if (((400 <= size.width && size.width <= 4000) && (400 <= size.height && size.height <= 4000)) && ((size.width) == (size.height))) {

                optimalSize = size;

                break;
            }

        }
        /**如果没找到16/9的就选择最接近的*/
        if (optimalSize == null) {

            double dMin = 100.0;
            Camera.Size RightSize = null;

            for (Camera.Size size : sizes) {

//                LogUtils.i("图片的宽高:    width:" + size.width + "     height:" + size.height+"     目标:"+width +"   abs: "+abs);
                double fRate = size.width / (float) size.height;
                double fDistance = Math.abs(fRate - 1);

                //找最接近16比9的size;//这里每次更新,都会将dMin 重置 , 这里相当于取最小值的意思 , 前一个和后一个对比 有跟小的上
                //如此就可以找到最合适的尺寸
                if (fDistance < dMin) {
                    dMin = fDistance;
                    RightSize = size;
                }

            }


            //最接近的值赋给变量optimalSize
            optimalSize = RightSize;

        }

        Logger.myLog("最佳图片的宽高:    width:" + optimalSize.width + "     height:" + optimalSize.height);
        return optimalSize;
    }


}
