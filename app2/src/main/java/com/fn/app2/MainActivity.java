package com.fn.app2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * google 提供的 ocr
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

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

    /**
     * 权限请求值
     */
    private static final int PERMISSION_REQUEST_CODE=0;
    private TextView mTv;
    private ImageView mIv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }

        //Android6.0之前安装时就能复制，6.0之后要先请求权限，所以6.0以上的这个方法无用。
        copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME);

        initView();

    }

    private void initView() {

        mIv = (ImageView) findViewById(R.id.iv);
        mTv = (TextView) findViewById(R.id.tv);
        Button but = (Button) findViewById(R.id.but);
        but.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "run: kaishi " + System.currentTimeMillis());
                        @SuppressLint("ResourceType") InputStream is = getResources().openRawResource(R.drawable.test3);

                        Bitmap mBitmap = BitmapFactory.decodeStream(is);
                        Log.i(TAG, "run: bitmap " + System.currentTimeMillis());

                        TessBaseAPI tessBaseAPI = new TessBaseAPI();

                        tessBaseAPI.init(DATAPATH, DEFAULT_LANGUAGE);

                        tessBaseAPI.setImage(mBitmap);

                        final String text = tessBaseAPI.getUTF8Text();

                        Log.i(TAG, "run: text " + System.currentTimeMillis() + text);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTv.setText(text);
                            }
                        });

                        tessBaseAPI.clear();
                        tessBaseAPI.end();
                    }
                }).start();
            }
        });
    }

    /**
     * 将assets中的识别库复制到SD卡中
     * @param path  要存放在SD卡中的 完整的文件名。这里是"/storage/emulated/0/tessdata/chi_sim.traineddata"
     * @param name  assets中的文件名 这里是 "chi_sim.traineddata"
     */
    public void copyToSD(String path, String name) {
        Log.i(TAG, "copyToSD: "+path);
        Log.i(TAG, "copyToSD: "+name);

        //如果存在就删掉
        File f = new File(path);
        if (f.exists()){
            f.delete();
        }
        if (!f.exists()){
            File p = new File(f.getParent());
            if (!p.exists()){
                p.mkdirs();
            }
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InputStream is=null;
        OutputStream os=null;
        try {
            is = this.getAssets().open(name);
            File file = new File(path);
            os = new FileOutputStream(file);
            byte[] bytes = new byte[2048];
            int len = 0;
            while ((len = is.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null)
                    is.close();
                if (os != null)
                    os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 请求到权限后在这里复制识别库
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: "+grantResults[0]);
        switch (requestCode){
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    Log.i(TAG, "onRequestPermissionsResult: copy");
                    copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME);
                }
                break;
            default:
                break;
        }
    }

    /**
     * byte转Bitmap
     *
     * @param b
     * @return
     */
    public Bitmap Bytes2Bimap(byte[] b) {
        if (b.length != 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//            options.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(b, 0, b.length, options);
            return bitmap;
        } else {
            return null;
        }
    }

}
