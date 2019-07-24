package com.fn.camera1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.fn.camera1.utils.TesseractUtil;
import com.fn.camera1.view.PreviewFrame;
import com.fn.loading_library.FnProgressDialog;
import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PreviewFrame.OnScanListener {

    /**
     * TessBaseAPI初始化用到的第一个参数，是个目录。
     */
    private static final String DATAPATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    /**
     * 在DATAPATH中新建这个目录，TessBaseAPI初始化要求必须有这个目录。
     */
    private static final String tessdata = DATAPATH + File.separator + "tessdata";

    /**
     * TessBaseAPI初始化测第二个参数，就是识别库的名字不要后缀名。
     */
    private static final String DEFAULT_LANGUAGE = "chi_sim";

    /**
     * assets中的文件名
     */
    private static final String DEFAULT_LANGUAGE_NAME = DEFAULT_LANGUAGE + ".traineddata";

    /**
     * 保存到SD卡中的完整文件名
     */
    private static final String LANGUAGE_PATH = tessdata + File.separator + DEFAULT_LANGUAGE_NAME;

    private CameraView mCameraView;
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final String TAG = "MainActivity";
    private Handler mBackgroundHandler;

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };
    private ImageView mImageView;
    Bitmap bitmap = null;
    private boolean isCliping = false;
    private boolean isDisc = false;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

        }
    };
    ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();


    private ImRunnable mImRunnable;
    private TessBaseAPI mTessBaseAPI;
    private PreviewFrame mPreviewFrame;
    private int mAnimatedValue;
    private int mIcCardHeight;
    private AsyncTask<Void, Void, String> mExecute;
    private boolean isOutOfBorder;
    private boolean mIsLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        initView();

    }

    private void initHandler() {
        mImRunnable = new ImRunnable();
        mHandler.removeCallbacks(mImRunnable);

        mHandler.postDelayed(mImRunnable, 200);//延时间检测系统通知
    }


    //执行延时任务
    private class ImRunnable implements Runnable {
        @Override
        public void run() {

            //重复执行本身
            mHandler.postDelayed(this, 1000);
        }
    }

    private void initView() {

        mCameraView = (CameraView) findViewById(R.id.camera);

        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }

        ImageButton fab = (ImageButton) findViewById(R.id.take_picture);

        fab.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        Toast.makeText(MainActivity.this, "松手开始识别", Toast.LENGTH_SHORT).show();
                        break;

                    case MotionEvent.ACTION_UP:
                        shiBie();
                        break;

                }

                return true;
            }
        });

        mImageView = (ImageView) findViewById(R.id.iv);

        mPreviewFrame = (PreviewFrame) findViewById(R.id.previewFrame);
        mPreviewFrame.setOnScanListener(this);

    }

    @Override
    public void canClipBitmap(int animatedValue, int icCardHeight) {
        this.mAnimatedValue = animatedValue;
        this.mIcCardHeight = icCardHeight;

    }

    /**
     * 从View上截图 返回 bitmap
     *
     * @param v
     * @return
     */
    public static Bitmap loadBitmapFromView(View v) {
        if (v == null) {
            return null;
        }
        Bitmap screenshot;
        //创建一个和view 一样大小的画布,
        screenshot = Bitmap.createBitmap(v.getWidth(), 675, Bitmap.Config.ARGB_8888);
        //以screenshot 为参照物拿到画板
        Canvas canvas = new Canvas(screenshot);
        canvas.translate(-v.getScrollX(), -v.getScrollY());//我们在用滑动View获得它的Bitmap时候，获得的是整个View的区域（包括隐藏的），如果想得到当前区域，需要重新定位到当前可显示的区域
        // 将 view 画到画布上
        v.draw(canvas);

        return screenshot;
    }

    /**
     * 截图方式 一
     * 此种方式比较简单只需传入当前要截取屏幕的Activity对象即可，不需要添加任何权限，后续可将截图的bitmap保存到本地即可；
     * 缺点：无法截取WebView页面，截屏后是白屏！
     *
     * @param activity
     * @return
     */
    public static Bitmap capture(Activity activity) {
        activity.getWindow().getDecorView().setDrawingCacheEnabled(true);
        Bitmap bmp = activity.getWindow().getDecorView().getDrawingCache();
        return bmp;
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            mCameraView.start();

        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

            MainActivity.ConfirmationDialogFragment.newInstance(
                    R.string.camera_permission_confirmation,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION,
                    R.string.camera_permission_not_granted)
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }


    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mImRunnable);
        mCameraView.stop();
        mPreviewFrame.cancelAnimator();//todo 这个是必须的 , 不要忘了 , 不然会一直在后台运行 , 耗费资源 ,
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
    }



    private CameraView.Callback mCallback = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            super.onCameraOpened(cameraView);

            Log.d(TAG, "onCameraOpened() called with: cameraView = [" + cameraView + "]");

            if (mCameraView != null) {
                //此处为识别图片的操作
                //todo 修改预览的尺寸,尽量小
                mCameraView.setFlash(FLASH_OPTIONS[0]);
                mCameraView.setAspectRatio(AspectRatio.of(1920, 1080));
                mCameraView.setPictureSize(1080, 1920);
                mCameraView.setAutoFocus(true);

                mTessBaseAPI = new TessBaseAPI();
                mTessBaseAPI.init(DATAPATH, DEFAULT_LANGUAGE);
            }

        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            super.onCameraClosed(cameraView);

            Log.d(TAG, "onCameraClosed() called with: cameraView = [" + cameraView + "]");

        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            super.onPictureTaken(cameraView, data);

            Log.d(TAG, "onPictureTaken() called with: cameraView = [" + cameraView + "], data = [" + data + "]");

//            Glide.with(MainActivity.this).load(data).into(mImageView);

        }

        @Override
        public void onPreviewFrame(final byte[] data, final Camera camera) {
            super.onPreviewFrame(data, camera);

            //帧布局的扫描回调
            if (isDisc == false) {

                if (mIsLoading == false) {

                    if (data != null && data.length > 0) {

                        Log.i(TAG, "在主界面中实时预览 --- data.size : " + data.length);
                        // bitmap = cameraByte2Bitmap(data, 1920, 1080);//第一种图片解析方式
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {

                                bitmap = byte2bitmap(data, camera);//转换为 bitmap //第二种图片解析方式

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (bitmap != null) {
                                            mImageView.setImageBitmap(bitmap);
//                                            shiBie();
                                        }
                                    }
                                });

                                Log.i(TAG, "run: 线程池中 Runnable :" + Thread.currentThread());
                            }
                        };

                        singleThreadExecutor.execute(runnable);

                    }
                }

            }

        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {

            case REQUEST_CAMERA_PERMISSION:

                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }

                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();

                }

                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                                                             String[] permissions, int requestCode, @StringRes int notGrantedMessage) {

            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }


    private synchronized Bitmap byte2bitmap(byte[] bytes, Camera camera) {

        mIsLoading = true;
        Bitmap bitmap = null;
        ByteArrayOutputStream os = null;
        try {

            Camera.Size size = camera.getParameters().getPreviewSize(); // 获取预览大小
            final int w = size.width; // 宽度
            final int h = size.height;
            final YuvImage image = new YuvImage(bytes, ImageFormat.NV21, w, h, null);

            os = new ByteArrayOutputStream(bytes.length);
            if (!image.compressToJpeg(new Rect(0, 0, w, h), 80, os)) {
                return null;
            }
            byte[] tmp = os.toByteArray();
            bitmap = BitmapFactory.decodeByteArray(tmp, 0, tmp.length);
            bitmap = rotateToDegrees(bitmap, 90);//旋转90度

            //todo 在这里裁剪出需要的部分 bitmap 然后显示在界面上

            //这里设置的裁剪宽度不能大于原始图片的宽度
            bitmap = bitmapCrop(bitmap, 360, 565, 1050 - 360, 645 - 565);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                    mIsLoading = false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }

    public int dp2px(int paramInt) {
        DisplayMetrics localDisplayMetrics = getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(1, paramInt, localDisplayMetrics);
    }


    /**
     * 从给定路径加载图片
     */
    public static Bitmap loadBitmap(String imgpath) {
        return BitmapFactory.decodeFile(imgpath);
    }

    int n = 0;

    private void shiBie() {

        if (mCameraView.isCameraOpened()) {

            mExecute = new AsyncTask<Void, Void, String>() {

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    isDisc = true;
                    FnProgressDialog.showDialog(MainActivity.this);
                    n++;
                }

                @Override
                protected String doInBackground(Void... voids) {

                    if (isDisc == true && mTessBaseAPI != null && bitmap != null) {

                        Log.i(TAG, "doInBackground: 正在执行: " + n);
//                        Bitmap grayBitmap = TesseractUtil.getInstance().catchPhoneRect(bitmap, mImageView);
                        if (bitmap != null) {
                            mTessBaseAPI.setImage(bitmap);
                            String text = mTessBaseAPI.getUTF8Text();

                            Log.i(TAG, "run: 识别出的文字:  time: " + System.currentTimeMillis() + " --> 文字: " + text);
                            return text;

                        } else {
                            return null;
                        }

                    } else {

                        return null;

                    }


                }

                @Override
                protected void onPostExecute(String text) {
                    super.onPostExecute(text);
                    FnProgressDialog.closeDialog();
                    isDisc = false;
                    if (TextUtils.isEmpty(text)) {
                        Toast.makeText(MainActivity.this, " --> 文字: 识别出错!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, " --> 文字: " + text, Toast.LENGTH_SHORT).show();
                    }
                    Log.i(TAG, "doInBackground: 执行了: " + n);


                }
            }.execute();


        }
    }


    /**
     * 将彩色图转换为灰度图
     *
     * @param img 位图
     * @return 返回转换好的位图
     */
    public Bitmap convertGreyImg(Bitmap img) {
        int width = img.getWidth();         //获取位图的宽
        int height = img.getHeight();       //获取位图的高

        int[] pixels = new int[width * height]; //通过位图的大小创建像素点数组

        img.getPixels(pixels, 0, width, 0, 0, width, height);
        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];

                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);

                grey = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                grey = alpha | (grey << 16) | (grey << 8) | grey;
                pixels[width * i + j] = grey;
            }
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static boolean isEmptyBitmap(final Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }


    /**
     * 对图片进行灰度化处理
     *
     * @param bm 原始图片
     * @return 灰度化图片
     */
    public static Bitmap getGrayBitmap(Bitmap bm) {
        Bitmap bitmap = null;
        //获取图片的宽和高
        int width = bm.getWidth();
        int height = bm.getHeight();
        //创建灰度图片
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //创建画布
        Canvas canvas = new Canvas(bitmap);
        //创建画笔
        Paint paint = new Paint();
        //创建颜色矩阵
        ColorMatrix matrix = new ColorMatrix();
        //设置颜色矩阵的饱和度:0代表灰色,1表示原图
        matrix.setSaturation(0);
        //颜色过滤器
        ColorMatrixColorFilter cmcf = new ColorMatrixColorFilter(matrix);
        //设置画笔颜色过滤器
        paint.setColorFilter(cmcf);
        //画图
        canvas.drawBitmap(bm, 0, 0, paint);
        return bitmap;
    }

    /**
     * 图片旋转
     *
     * @param tmpBitmap
     * @param degrees
     * @return
     */
    public static Bitmap rotateToDegrees(Bitmap tmpBitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.setRotate(degrees);
        return Bitmap.createBitmap(tmpBitmap, 0, 0, tmpBitmap.getWidth(), tmpBitmap.getHeight(), matrix, true);

    }


    /**
     * Bitmap裁剪
     *
     * @param bitmap 原图
     * @param width  宽
     * @param height 高
     */
    public Bitmap bitmapCrop(Bitmap bitmap, int left, int top, int width, int height) {
        if (null == bitmap || width <= 0 || height < 0) {
            return null;
        }
        int widthOrg = bitmap.getWidth();
        int heightOrg = bitmap.getHeight();
        if (widthOrg >= width && heightOrg >= height) {
            try {
                bitmap = Bitmap.createBitmap(bitmap, left, top, width, height);
            } catch (Exception e) {
                return null;
            }
        }
        return bitmap;
    }

    /**
     * 转为二值图像 并判断图像中是否可能有手机号
     *
     * @param bmp 原图bitmap
     * @param tmp 二值化阈值 超出阈值的像素置为白色，否则为黑色
     * @return
     */
    public Bitmap convertToBMW(final Bitmap bmp, int tmp) {
        int width = bmp.getWidth(); // 获取位图的宽
        int height = bmp.getHeight(); // 获取位图的高
        int[] pixels = new int[width * height]; // 通过位图的大小创建像素点数组
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);//得到图片的所有像素


        int lineHeight = 0;//当前记录的一行文字已经累计的高度，每次遇到一行有黑色像素点时 +1

        //目标行，每遇到一个黑色像素，就会+1，本行就不会在记录lineHeight，下一行在遇到黑色像素，就继续+1，保证每行lineHeight最多 +1 一次
        int row = 0;

        //当前记录的一行文字是否超出边缘（如果第一行就发现黑色像素，就为true了，直到遇到空白行，还原false）
        boolean isOutOfRect = false;


        //最终捕捉到的单行文字在图片中的矩形区域
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];
                // 分离三原色
                alpha = ((grey & 0xFF000000) >> 24);
                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);
                if (red > tmp) {
                    red = 255;
                } else {
                    red = 0;
                }
                if (blue > tmp) {
                    blue = 255;
                } else {
                    blue = 0;
                }
                if (green > tmp) {
                    green = 255;
                } else {
                    green = 0;
                }
                pixels[width * i + j] = alpha << 24 | red << 16 | green << 8
                        | blue;

                //这里是二值化化的判断，if里是白色，else里是黑色
                if (pixels[width * i + j] == -1 || (i == height - 1 && j == width - 1)) {
                    //将当前像素赋值为白色
                    pixels[width * i + j] = -1;

                    /**
                     lineHeight>0 : 如果当前记录行的文字高度大于0
                     row == i : 当前是不是目标行，每行第一次发现黑色像素就会+1，所以只有当前行还没出现黑色像素时，才会 == i
                     j == width - 1 : 当前像素是不是本行的最后一个像素点
                     综上所述，这里的判断条件为 ： 已经捕捉到一行文字，而且这一行已经结束了还没发现黑色像素，这行文字该结束了
                     */
                    if (lineHeight > 0 && row == i && j == width - 1) {
                        //这行文字是不是超出边缘的文字，如果是，直接跳过，开始记录下一行
                        if (!isOutOfBorder) {
                            //跟上一行的文字高度比较，记录下高度更高的一行文字的top 和 bottom
                            int h = bottom - top;
                            if (lineHeight > h) {
                                //这里我把top 和 bottom 都加了1/4的行高，为了有一点留白，其实加不加无所谓
                                top = i - lineHeight - (lineHeight / 4);
                                bottom = i - 1 + (lineHeight / 4);
                            }
                        }
                        //这行文字既然已经结束了，下一行文字肯定不是超出边缘的了
                        isOutOfRect = false;
                        //上一行文字已经处理完成，行高归0，开始记录下一行
                        lineHeight = 0;
                    }
                } else {
                    //这里是黑色像素，将当前像素点赋值为黑色
                    pixels[width * i + j] = -16777216;
                    //如果当前行 = 目标行（遇到这行第一个黑色像素就会+1，到下一行才会相等）
                    if (i >= row) {
                        //如果当前的黑色像素 位于第一行像素  或 最后一行像素，那就是超出边缘的文字
                        if (i == 0 || i == height - 1)
                            isOutOfRect = true;
                        //行高+1
                        lineHeight++;
                        //目标行转移到下一行
                        row = i + 1;
                    }
                }
            }
        }

        /** 如果通过第一次过滤后，没有找到一行有意义的文字，或者找到了，文字高度占比还不到解析图片的20%,
         那这张图片八成是无意义的图片，不用解析，直接下一帧（当你对着墙或者什么无聊的东西扫描的时候，
         这里就会直接结束，不会浪费时间去做文字识别）
         */
        if (bottom - top < height * 0.2f) {
//            isScanning = false;
            return null;
        }

        /**
         到这里，上面的筛选已经通过了，我们已经定位到了一行目标文字的 top  和 bottom
         接下来就要定位left 和 right 了
         还是需要遍历一次，不过只需要 top-bottom 正中间的一行像素，思路同上，通过文字间距
         来将这一行文字分成横向的几个文字块，至于区分条件，就看文字间的间隔，超过正常宽度就
         算是一个文字块的结束，至于正常的文字间隔就要按需求而定了，比如这里扫描手机号，手机
         号是11位的，那两个数字之间的距离说破天也不会超过图片宽度的  1/11，那我就定为1/11

         那问题又来了，如果刚好手机号在这块图像右边的上半部分，下半部分是在手机号左边的无用文字，
         只是因为高度重叠，上面取行高时被当成了一行，那这里只取top-bottom正中间的一条像素，
         遍历到手机号所在的右边一半时，不是只能找到空白像素？
         这就没办法了，只取一条像素行，一是为了减少耗时，二是让我的脑细胞少死一点，你要扫描手机号，
         还非要把手机号完美躲开正中间，那我就不管了.....
         */

        //文字间隔，每次遇到白色像素点+1，每次遇到黑色像素点归0，当space > 宽度的1/11时，就算超过正常文字间距了
        int space = 0;

        //当前文字块宽度，每当遇黑色像素点时，更新宽度，space 超过宽度的1/11时，归0，文字块结束
        int textWidth = 0;
        //当前文字开始X坐标，文字块宽度 = 结束点 - startX
        int startX = 0;
        //遍历top-bottom 正中间一行像素
        for (int j = 0; j < width; j++) {
            //如果是白色像素
            if (pixels[width * (top + (bottom - top) / 2) + j] == -1) {
                /**
                 如果已经捕捉到了文字块，而且space > width / 11 或者已经遍历结束了，
                 那这个文字块的宽度就取到了
                 */
                if (textWidth > 0 && (space > width / 11 || j == width - 1)) {
                    //同高度一样，比较上一个文字块的宽度，留下最大的一个 top 和 bottom
                    if (textWidth > right - left) {
                        //这里取left 和 right 一样加了一个space/2的留白
                        left = j - space - textWidth - (space / 2);
                        right = j - 1;
                    }
                    //既然当前文字块已经结束，就把参数重置，继续捕捉下一个文字块
                    space = 0;
                    startX = 0;
                }
                space++;
            } else {
                //这里是黑色像素
                //记录文字块的开始X坐标
                if (startX == 0)
                    startX = j;
                //文字块当前宽度
                textWidth = j - startX;
                //文字间隔归0
                space = 0;
            }
        }

        //如果最终捕捉到的文字块，宽度还不到图片宽度的30%，同样跳过，八成不是手机号，就不要浪费时间识别了
        if (right - left < width * 0.3f) {
//            isScanning = false;
            return null;
        }

        /**
         到这里 已经捕捉到了一个很可能是手机号码的文字块，区域就是  left、top、right、bottom
         把这个区域的像素，取出来放到一个新的像素数组
         */
        int targetWidth = right - left;
        int targetHeight = bottom - top;
        int[] targetPixels = new int[targetWidth * targetHeight];
        int index = 0;
        for (int i = top; i < bottom; i++) {
            for (int j = left; j < right; j++) {
                if (index < targetPixels.length)
                    targetPixels[index] = pixels[width * i + j];
                index++;
            }
        }
        //销毁之前的图片
        bmp.recycle();

        // 新建图片
        final Bitmap newBmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        //把捕捉到的图块，写进新的bitmap中
        newBmp.setPixels(targetPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        //将裁切的图片显示出来（测试用，需要为CameraView  setTag（ImageView））
        //主线程 {
        //    @Override
        //    public void run() {
        //        ImageView imageView = (ImageView) getTag();
        //        imageView.setVisibility(View.VISIBLE);
        //        imageView.setImageBitmap(newBmp);
        //    }
        //};

        //这里可以把这块图像保存到本地，可以做个按钮，点击时把saveBmp=true,就可以采集一张，采集几张之后，拿去做tesseract 训练，训练出适合自己需求的字库，才是提高效率的关键
        //   if (saveBmp) {
//           saveBmp = false;
//           ImageUtils.saveBitmap(scanBmp, System.currentTimeMillis() + ".jpg");
//           }
        //返回需要交给tess-two识别的内容
        return newBmp;
    }


    @Override
    public void onBackPressed() {
        mExecute.cancel(true);
//        FnProgressDialog.closeDialog();
        super.onBackPressed();
    }
}
