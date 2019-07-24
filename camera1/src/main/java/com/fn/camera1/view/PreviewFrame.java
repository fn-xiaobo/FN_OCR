package com.fn.camera1.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Created by 77167 on 2018/10/11.
 */

public class PreviewFrame extends View {

    private static float DEFAULT_WIDTH = 1080;//默认的 X 轴 高度
    private static float DEFAULT_HEIGHT = 1920;//默认的 Y 轴 高度
    private static int IC_CARD_HEIGHT = 675;//默认证件高度


    private Paint mPaint = new Paint();
    private Paint mLinePaint = new Paint();
    private RectF mRrect;
    private int mDy = 0;//动画中不断变化的值
    private OnScanListener mOnScanListener;//可以扫描的监听
    private ValueAnimator mValueAnimator;
    private int mWidthMeasureSpec;
    private int mHeightMeasureSpec;

    public PreviewFrame(Context context) {
        this(context, null);
    }

    public PreviewFrame(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewFrame(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        //todo 初始化画笔

        //禁用硬件加速 标配
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        startAnimator();//开始动画

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //todo 指定显示时的宽高

        //取出当前宽高的测量模式
        int width_mode = MeasureSpec.getMode(widthMeasureSpec);
        int height_mode = MeasureSpec.getMode(heightMeasureSpec);

        //测量得到当前的真实宽高
        int measure_width_size = MeasureSpec.getSize(widthMeasureSpec);
        int measure_height_size = MeasureSpec.getSize(heightMeasureSpec);

        //取出用户设置的值
        DEFAULT_WIDTH = Math.max(DEFAULT_WIDTH, measure_width_size);
        DEFAULT_HEIGHT = Math.max(DEFAULT_HEIGHT, measure_height_size);

        if (width_mode != MeasureSpec.EXACTLY) {

            int exceptWidth = (int) DEFAULT_WIDTH;

            widthMeasureSpec = MeasureSpec.makeMeasureSpec(exceptWidth, MeasureSpec.EXACTLY);

            this.mWidthMeasureSpec = widthMeasureSpec;
        }

        if (height_mode != MeasureSpec.EXACTLY) {

            int exceptHeight = (int) DEFAULT_HEIGHT;

            heightMeasureSpec = MeasureSpec.makeMeasureSpec(exceptHeight, MeasureSpec.EXACTLY);

            this.mHeightMeasureSpec = widthMeasureSpec;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //todo 在 View 上绘制

        mLinePaint.setColor(Color.RED);
        mLinePaint.setStrokeWidth(4);
        mLinePaint.setAntiAlias(true);//去锯齿
        mLinePaint.setDither(true);//去抖动

        //新建图层
        int layerID = canvas.saveLayer(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT, mPaint, Canvas.ALL_SAVE_FLAG);

        //设置背景色 , 半透明灰色
        canvas.drawColor(0x99666666);

        //绘制透明区域
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mPaint.setColor(0xFFFFCC44);
        mRrect = new RectF(0, 0, DEFAULT_WIDTH, 675);
        canvas.drawRect(mRrect, mPaint);

        //最后将画笔去除Xfermode
        mPaint.setXfermode(null);

        //todo 绘制四个角落的图标
        //Y轴
        Log.i("fn_tag", "onDraw: getPaddingLeft -->: " + getPaddingLeft() + " getPaddingTop -->: " + getPaddingTop() + " getPaddingLeft -->: " + getPaddingLeft() + " DEFAULT_HEIGHT - getPaddingBottom() -->: " + (DEFAULT_HEIGHT - getPaddingBottom()));
        //getPaddingLeft -->: 0    getPaddingTop -->: 0      getPaddingLeft -->: 0     DEFAULT_HEIGHT - getPaddingBottom() -->: 1920.0
        //float             startX, float          startY, float        stopX, float                                             stopY,                                         @NonNull Paint paint
        mLinePaint.setStrokeWidth(4);
        mLinePaint.setColor(Color.RED);
        //左上角的角
        canvas.drawLine(10, 10, 100, 10, mLinePaint);
        canvas.drawLine(10, 10, 10, 100, mLinePaint);

        //右上角的角
        canvas.drawLine(DEFAULT_WIDTH - 100 - 10, 10, DEFAULT_WIDTH - 10, 10, mLinePaint);
        canvas.drawLine(DEFAULT_WIDTH - 10, 10, DEFAULT_WIDTH - 10, 100, mLinePaint);

        //左下角的角
        canvas.drawLine(10, IC_CARD_HEIGHT - 100 - 10, 10, IC_CARD_HEIGHT - 10, mLinePaint);
        canvas.drawLine(10, IC_CARD_HEIGHT - 10, 100, IC_CARD_HEIGHT - 10, mLinePaint);
//
//        //右下角的角
        canvas.drawLine(DEFAULT_WIDTH - 10, IC_CARD_HEIGHT - 10, DEFAULT_WIDTH - 100 - 10, IC_CARD_HEIGHT - 10, mLinePaint);
        canvas.drawLine(DEFAULT_WIDTH - 10, IC_CARD_HEIGHT - 10, DEFAULT_WIDTH - 10, IC_CARD_HEIGHT - 100 - 10, mLinePaint);

        //身份证识别窗口
        mLinePaint.setColor(Color.WHITE);
        mLinePaint.setStrokeWidth(1);
        mLinePaint.setAntiAlias(true);//去锯齿
        mLinePaint.setDither(true);//去抖动
        mLinePaint.setTextSize(30);
        canvas.drawText("请将身份证号对准到框中", DEFAULT_WIDTH / 3, IC_CARD_HEIGHT - 140, mLinePaint);

        //矩形框 用来框住身份证号码
        mLinePaint.setColor(Color.WHITE);
        mLinePaint.setStrokeWidth(5);
        mLinePaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(DEFAULT_WIDTH / 3, IC_CARD_HEIGHT - 110, DEFAULT_WIDTH - 30, IC_CARD_HEIGHT - 30, mLinePaint);

        float left = DEFAULT_WIDTH / 3;
        int top = IC_CARD_HEIGHT - 110;
        float right = DEFAULT_WIDTH - 30;
        int bottom = IC_CARD_HEIGHT - 30;
        Log.i("fn_tag", "识别框的位置: left: " + left + " top: " + top + " right: " + right + " bottom: " + bottom);

        //todo 绘制扫描线
        mLinePaint.setStrokeWidth(6);
        mLinePaint.setColor(Color.GREEN);
        canvas.drawLine(20, mDy - 10, DEFAULT_WIDTH - 20, mDy - 10, mLinePaint);

        //还原图层
        canvas.restoreToCount(layerID);

    }

    //扫描动画
    private void startAnimator() {
        mValueAnimator = ValueAnimator.ofInt(0, IC_CARD_HEIGHT);
        mValueAnimator.setDuration(3000);
        mValueAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mValueAnimator.setInterpolator(new LinearInterpolator());
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mDy = (int) animation.getAnimatedValue();
                Log.i("fn_tag", "onAnimationUpdate: dy: " + mDy);
                postInvalidate();
                if (mOnScanListener != null) {
                    mOnScanListener.canClipBitmap(mDy, IC_CARD_HEIGHT);
                }

            }
        });
        mValueAnimator.start();

    }

    //todo 取消动画,这一步必须在界面退出前调用
    public void cancelAnimator() {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
        }
    }


    //对外的扫描接口回调
    public interface OnScanListener {
        void canClipBitmap(int animatedValue, int icCardHeight);
    }

    public void setOnScanListener(OnScanListener onScanListener) {
        this.mOnScanListener = onScanListener;
    }

}
