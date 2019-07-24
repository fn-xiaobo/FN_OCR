package com.fn.camera1.utils;

import android.graphics.Bitmap;
import android.support.annotation.MainThread;
import android.view.View;
import android.widget.ImageView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.Stack;

/**
 * Created by 77167 on 2018/10/11.
 * 来源:
 * https://blog.csdn.net/mr_sk/article/details/72877492?utm_source=copy
 */

public class TesseractUtil {

    private static TesseractUtil mTesseractUtil = null;
    private float proportion = 0.5f;

    private TesseractUtil() {

    }

    public static TesseractUtil getInstance() {
        if (mTesseractUtil == null)
            synchronized (TesseractUtil.class) {
                if (mTesseractUtil == null)
                    mTesseractUtil = new TesseractUtil();
            }
        return mTesseractUtil;
    }

    /**
     * 调整阈值
     *
     * @param pro 调整比例
     */
    public int adjustThresh(float pro) {
        this.proportion += pro;

        if (proportion > 1f)
            proportion = 1f;
        if (proportion < 0)
            proportion = 0;
        return (int) (proportion * 100);
    }


    //白色色值
    private final int PX_WHITE = -1;

    //黑色色值
    private final int PX_BLACK = -16777216;

    // 占位色值（这个算法加入了排除干扰的模式，如果在捕捉一个文字的位置时，发现文字的宽度或者高度超出了正常高度，
    // 则很有可能这里被水印之类的干扰了，那就把超出正常的范围像素色值变成-2，颜色和白色很接近，会被当作背景色，
    // 相当于清除了干扰，不直接变成-1是为了在其他数字被误判为干扰水印时，可以还原）
    private final int PX_UNKNOW = -2;

    /**
     * 转为二值图像 并判断图像中是否可能有手机号
     *
     * @param bmp 原图bitmap
     * @param imageView 显示当前图片处理进度，测试用
     * @return
     */
    public Bitmap catchPhoneRect(final Bitmap bmp, ImageView imageView) {
        int width = bmp.getWidth(); // 获取位图的宽
        int height = bmp.getHeight(); // 获取位图的高
        int[] pixels = new int[width * height]; // 通过位图的大小创建像素点数组
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width;
        int top = height;
        int right = 0;
        int bottom = 0;

        //计算阈值
        measureThresh(pixels, width, height);

        /**
         * 二值化
         * */
        binarization(pixels, width, height);

        int space = 0;
        int textWidth = 0;
        int startX = 0;
        int centerY = height / 2 - 1;
        int textLength = 0;
        int textStartX = 0;

        /**
         * 遍历中间一行像素，粗略捕捉手机号
         * 在扫描框中定义了一条中心线，如果每次扫描使用中心线来对准手机号，那么捕捉手机号的速度和准确度都有了很大的提高
         * 实现逻辑：先对从帧数据中裁切好的图片进行二值化，然后取最中间一行的像素遍历，初步判断是否可能含有手机号
         * 即遍历这一行时，每次遇到一段连续黑色像素，就记录一次textLength++（没一段黑色像素代表一个笔画的宽度），手机号都是11位数字
         * 由此可以得知，合理的笔画范围是 最少：11111111111 ，拦腰遍历，会得到11个笔画宽度，textLength=11
         *                           最多：00000000000 ，拦腰遍历，会得到22个笔画宽度，textLength=22
         * 也就是说，最中间一行遍历完成如果 textLength>11 && textLength<22  表示有一定可能有手机号存在(同时得到文字块的left、right)，否则一定不存在，直接跳过，解析下一帧
         * */
        for (int j = 0; j < width; j++) {

            if (pixels[width * centerY + j] == PX_WHITE) {
                //白色像素，如果发现了连续黑色像素，到这里出现第一个白色像素，那么一个笔画宽度就确认了，textLength++
                if (space == 1)
                    textLength++;
                if (textWidth > 0 && startX > 0 && startX < height - 1 && (space > width / 10 || j == width - 1)) {
                    //如果捕捉到的合理的比划截面，就更新left、right ，如果出现了多个合理的文字块，就取宽度最大的
                    if (textLength > 10 && textLength < 22)
                        if (textWidth > right - left) {

                            left = j - space - textWidth - (space / 2);
                            if (left < 0)
                                left = 0;

                            right = j - 1 - (space / 2);
                            if (right > width)
                                right = width - 1;
                            textStartX = startX;
                        }
                    textLength = 0;
                    space = 0;
                    startX = 0;
                }
                space++;
            } else {
                //一段连续黑色像素的开始坐标
                if (startX == 0)
                    startX = j;
                //文字块的宽度
                textWidth = j - startX;

                space = 0;
            }

        }

        //如果宽度占比过小，直接跳过
        if (right - left < width * 0.3f) {
            if (imageView != null) {
                bmp.setPixels(pixels, 0, width, 0, 0, width, height);
                //将裁切的图片显示出来
//                showImage(bmp, imageView);
            } else
                bmp.recycle();
            return null;
        }


        /**
         *粗略计算文字高度
         *这里先粗略取一块高度，确定包含文字，现在已经得知了文字块宽度，那么合理的字符宽度就是 (right - left) / 11，数字通常高度更大，这里就算宽度的1.5倍，然后为了确保包含文字，在中间线的上下各加一个文字高度
         *接下来就要捕捉文字块的具体信息了，包括精准的宽度、高度 以及 字符数量
         */
        top = (int) (centerY - (right - left) / 11 * 1.5);
        bottom = (int) (centerY + (right - left) / 11 * 1.5);
        if (top < 0)
            top = 0;
        if (bottom > height)
            bottom = height - 1;

        /**
         * 判断区域中有几个字符
         * */
        //已经使用过的像素标记
        int[] usedPixels = new int[width * height];
        int[] textRect = new int[]{right, bottom, 0, 0};
        //当前捕捉文字的rect
        int[] charRect = new int[]{textStartX, centerY, 0, centerY};
        //在文字块中捕捉到的字符个数
        int charCount = 0;
        //是否发现干扰
        boolean hasStain = false;
        startX = left;
        int charMaxWidth = (right - left) / 11;
        int charMaxHeight = (int) ((right - left) / 11 * 1.5);
        int charWidth = 0;//捕获到一个完整字符后得到标准的字符宽度
        boolean isInterfereClearing = false;
        //循环获取每一个字符的宽高位置
        while (true) {
            //当前字符的宽高是否正常
            boolean isNormal = false;
            //是否已经清除干扰，如果在捕捉字符宽高的过程中，发现有水印干扰，会调用clearInterfere()方法，将超出宽高的像素部分置为-2,然后继续捕捉下一个字符
            if (!isInterfereClearing) {
                //如果被水印干扰，在进行递归算法的过程中，高度或宽度会超出正常范围，这里会返回false，如果是没有水印的干净文字块，不会触发else（方法实现在下面）
                isNormal = catchCharRect(pixels, usedPixels, charRect, width, height, charMaxWidth, charMaxHeight, charRect[0], charRect[1]);
            } else
                isNormal = clearInterfere(pixels, usedPixels, charRect, width, height, charWidth, charWidth, charRect[0], charRect[1]);
            //记录已经捕捉的字符数量
            charCount++;

            if (!isNormal) {
                //如果第一个字符发现干扰，这里记录有干扰，然后捕捉下一个字符，如果还是有干扰，继续捕捉下一个，直到找到一个正常的字符，那就可以得到一个字符的精准宽度，和高度，然后根据这个正确的宽高，从头再遍历一次，这个就可以把超出这个宽高的像素置为-2
                hasStain = true;
                if (charWidth != 0) {
                    usedPixels = new int[width * height];
                    charRect = new int[]{textStartX, centerY, 0, centerY};
                    charCount = 0;
                    isInterfereClearing = true;
                }
            } else {
                //到这里就表示：发现了干扰，而且已经捕捉到一个正确的字符宽高，可以开始清除水印了
                if (hasStain && !isInterfereClearing) {
                    //把位置还原，重新开始遍历，这次一边获取宽高，一边清除水印
                    usedPixels = new int[width * height];
                    charWidth = charRect[3] - charRect[1];
                    charRect = new int[]{textStartX, centerY, 0, centerY};
                    charCount = 0;
                    isInterfereClearing = true;
                    continue;
                } else {
                    if (charWidth == 0) {
                        charWidth = charRect[3] - charRect[1];
                    }
                    //如果没有发现干扰，直接更新文字块的精准位置
                    if (textRect[0] > charRect[0])
                        textRect[0] = charRect[0];

                    if (textRect[1] > charRect[1])
                        textRect[1] = charRect[1];

                    if (textRect[2] < charRect[2])
                        textRect[2] = charRect[2];

                    if (textRect[3] < charRect[3])
                        textRect[3] = charRect[3];
                }
            }
            //是否找到下一个字符
            boolean isFoundChar = false;
            if (!hasStain || isInterfereClearing) {
                //获取下一个字符的rect开始点（如果上一个字符的宽高正常这里就可以直接找到下一个字符的起始坐标）
                for (int x = charRect[2] + 1; x <= right; x++)
                    if (pixels[width * centerY + x] != PX_WHITE) {
                        isFoundChar = true;
                        charRect[0] = x;
                        charRect[1] = centerY;
                        charRect[2] = 0;
                        charRect[3] = 0;
                        break;
                    }
            } else {
                //如果发现干扰，那么可能还没有得到合理的宽高，还是用开头获取笔画的方式，寻找下一个字符开始捕获的坐标，
                for (int x = left; x <= right; x++)
                    if (pixels[width * centerY + x] != PX_WHITE && pixels[width * centerY + x - 1] == PX_WHITE) {
                        if (x <= startX)
                            continue;
                        startX = x;
                        isFoundChar = true;
                        charRect[0] = x;
                        charRect[1] = centerY;
                        charRect[2] = x;
                        charRect[3] = centerY;
                        break;
                    }
            }
            if (!isFoundChar) {
                break;
            }
        }

        //得到文字块的精准位置
        left = textRect[0];
        top = textRect[1];
        right = textRect[2];
        bottom = textRect[3];

        //如果高度合理，且捕捉到的字符数量为11个，那么基本可以确定，这是一个11位的文字块，否则跳过，解析下一帧
        if (bottom - top > (right - left) / 5 || bottom - top == 0 || charCount != 11) {
            if (imageView != null) {
                bmp.setPixels(pixels, 0, width, 0, 0, width, height);
                //将裁切的图片显示出来
//                showImage(bmp, imageView);
            } else
                bmp.recycle();
            return null;
        }


        /**
         * 将最终捕捉到的手机号区域像素提取到新的数组
         * */
        int targetWidth = right - left;
        int targetHeight = bottom - top;
        int[] targetPixels = new int[targetWidth * targetHeight];
        int index = 0;

        for (int i = top; i < bottom; i++) {
            for (int j = left; j < right; j++) {
                if (index < targetPixels.length) {
                    if (pixels[width * i + j] == PX_WHITE)
                        targetPixels[index] = PX_WHITE;
                    else
                        targetPixels[index] = PX_BLACK;
                }
                index++;
            }
        }

        bmp.recycle();
        // 新建图片
        final Bitmap newBmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        newBmp.setPixels(targetPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        //将裁切的图片显示出来
//        if (imageView != null)
//            showImage(newBmp, imageView);

            return newBmp;

    }


    private final int MOVE_LEFT = 0;
    private final int MOVE_TOP = 1;
    private final int MOVE_RIGHT = 2;
    private final int MOVE_BOTTOM = 3;

    /**
     * 捕捉字符
     * 这里用递归的算法，从字符的第一个黑色像素，开始，分别进行上下左右的捕捉，如果相邻的像素，也是黑色，就可以扩大这个字符的定位，以此类推，最后得到的就是字符的准确宽高
     * 这里之所以没有用递归，而是用循环，是因为递归嵌套的层级太多，会导致 栈溢出
     */
    private boolean catchCharRect(int[] pixels, int[] used, int[] charRect, int width, int height, int maxWidth, int maxHeight, int x, int y) {
        int nowX = x;
        int nowY = y;
        //记录动作（）
        Stack<Integer> stepStack = new Stack<>();

        while (true) {
            if (used[width * nowY + nowX] == 0) {
                used[width * nowY + nowX] = -1;
                if (charRect[0] > nowX)
                    charRect[0] = nowX;

                if (charRect[1] > nowY)
                    charRect[1] = nowY;

                if (charRect[2] < nowX)
                    charRect[2] = nowX;

                if (charRect[3] < nowY)
                    charRect[3] = nowY;

                if (charRect[2] - charRect[0] > maxWidth) {
                    return false;
                }

                if (charRect[3] - charRect[1] > maxHeight) {
                    return false;
                }

                if (nowX == 0 || nowX >= width - 1 || nowY == 0 || nowY >= height - 1) {
                    return false;
                }


            }

            //当前像素的左边是否还有黑色像素点
            int leftX = nowX - 1;
            if (leftX >= 0 && pixels[width * nowY + leftX] != PX_WHITE && used[width * nowY + leftX] == 0) {
                nowX = leftX;
                stepStack.push(MOVE_LEFT);
                continue;
            }

            //当前像素的上边是否还有黑色像素点
            int topY = nowY - 1;
            if (topY >= 0 && pixels[width * topY + nowX] != PX_WHITE && used[width * topY + nowX] == 0) {
                nowY = topY;
                stepStack.push(MOVE_TOP);
                continue;
            }


            //当前像素的右边是否还有黑色像素点
            int rightX = nowX + 1;
            if (rightX < width && pixels[width * nowY + rightX] != PX_WHITE && used[width * nowY + rightX] == 0) {
                nowX = rightX;
                stepStack.push(MOVE_RIGHT);
                continue;
            }


            //当前像素的下边是否还有黑色像素点
            int bottomY = nowY + 1;
            if (bottomY < height && pixels[width * bottomY + nowX] != PX_WHITE && used[width * bottomY + nowX] == 0) {
                nowY = bottomY;
                stepStack.push(MOVE_BOTTOM);
                continue;
            }

            //用循环模拟递归，当一个像素的周围，没有发现未记录的黑色像素，就可以退回上一步，最终效果就和递归一样了，而且不会引起栈溢出
            if (stepStack.size() > 0) {
                int step = stepStack.pop();
                switch (step) {
                    case MOVE_LEFT:
                        nowX++;
                        break;
                    case MOVE_RIGHT:
                        nowX--;
                        break;
                    case MOVE_TOP:
                        nowY++;
                        break;
                    case MOVE_BOTTOM:
                        nowY--;
                        break;
                }
            } else {
                break;
            }
        }
        if (charRect[2] - charRect[0] == 0 || charRect[3] - charRect[1] == 0) {
            return false;
        }
        return true;
    }


    /**
     * 清除干扰
     * 和catchCharRect()方法原理一样，但是多了一个逻辑，即超出正常范围的黑色像素，会被当作干扰，置为-2，这一步会导致有些被干扰连在一起的多个字符都被清空，所以在捕捉其他字符时，当发现没有超出范围，又被置为-2的像素，就还原为黑色，这样最终就能实现大部分的水印被清除（只针对我遇到的文字底部的水印）
     */
    private final int WAIT_HANDLE = 0;//待处理像素
    private final int HANDLED = -1;//已处理像素
    private final int HANDLING = -2;//处理过但未处理完成的像素

    /**
     * 清除干扰
     */
    private boolean clearInterfere(int[] pixels, int[] used, int[] charRect, int width, int height, int maxWidth, int maxHeight, int x, int y) {
        int nowX = x;
        int nowY = y;
        //记录动作
        Stack<Integer> stepStack = new Stack<>();
        boolean needReset = true;
        while (true) {
            if (used[width * nowY + nowX] == WAIT_HANDLE) {
                used[width * nowY + nowX] = HANDLED;

                if (charRect[2] - charRect[0] <= maxWidth && charRect[3] - charRect[1] <= maxHeight) {
                    if (charRect[0] > nowX)
                        charRect[0] = nowX;

                    if (charRect[1] > nowY)
                        charRect[1] = nowY;

                    if (charRect[2] < nowX)
                        charRect[2] = nowX;

                    if (charRect[3] < nowY)
                        charRect[3] = nowY;
                } else {
                    if (needReset)
                        needReset = false;
                    used[width * nowY + nowX] = HANDLING;
                    pixels[width * nowY + nowX] = PX_UNKNOW;
                }
            } else if (pixels[width * nowY + nowX] == PX_UNKNOW) {

                if (charRect[2] - charRect[0] <= maxWidth && charRect[3] - charRect[1] <= maxHeight) {
                    pixels[width * nowY + nowX] = PX_BLACK;
                    if (charRect[0] > nowX)
                        charRect[0] = nowX;

                    if (charRect[1] > nowY)
                        charRect[1] = nowY;

                    if (charRect[2] < nowX)
                        charRect[2] = nowX;

                    if (charRect[3] < nowY)
                        charRect[3] = nowY;

                    used[width * nowY + nowX] = HANDLED;
                } else {
                    if (needReset)
                        needReset = false;
                }
            }


            //当前像素的左边是否还有黑色像素点
            int leftX = nowX - 1;
            int leftIndex = width * nowY + leftX;
            if (leftX >= 0 && pixels[leftIndex] != PX_WHITE && (used[leftIndex] == WAIT_HANDLE || (needReset && used[leftIndex] == HANDLING))) {
                nowX = leftX;
                stepStack.push(MOVE_LEFT);
                continue;
            }

            //当前像素的上边是否还有黑色像素点
            int topY = nowY - 1;
            int topIndex = width * topY + nowX;
            if (topY >= 0 && pixels[topIndex] != PX_WHITE && (used[topIndex] == WAIT_HANDLE || (needReset && used[topIndex] == HANDLING))) {
                nowY = topY;
                stepStack.push(MOVE_TOP);
                continue;
            }


            //当前像素的右边是否还有黑色像素点
            int rightX = nowX + 1;
            int rightIndex = width * nowY + rightX;
            if (rightX < width && pixels[rightIndex] != PX_WHITE && (used[rightIndex] == WAIT_HANDLE || (needReset && used[rightIndex] == HANDLING))) {
                nowX = rightX;
                stepStack.push(MOVE_RIGHT);
                continue;
            }


            //当前像素的下边是否还有黑色像素点
            int bottomY = nowY + 1;
            int bottomIndex = width * bottomY + nowX;
            if (bottomY < height && pixels[bottomIndex] != PX_WHITE && (used[bottomIndex] == WAIT_HANDLE || (needReset && used[bottomIndex] == HANDLING))) {
                nowY = bottomY;
                stepStack.push(MOVE_BOTTOM);
                continue;
            }

            if (stepStack.size() > 0) {
                int step = stepStack.pop();
                switch (step) {
                    case MOVE_LEFT:
                        nowX++;
                        break;
                    case MOVE_RIGHT:
                        nowX--;
                        break;
                    case MOVE_TOP:
                        nowY++;
                        break;
                    case MOVE_BOTTOM:
                        nowY--;
                        break;
                }
            } else {
                break;
            }
        }
        return true;
    }

    private int redThresh = 130;
    private int blueThresh = 130;
    private int greenThresh = 130;


    /**
     * 计算扫描线所在像素行的平均阈值
     * 我找了一些自动计算阈值的算法，都不太好用，这里就直接采集了中间一行像素的平均值
     */
    private void measureThresh(int[] pixels, int width, int height) {
        int centerY = height / 2;

        int redSum = 0;
        int blueSum = 0;
        int greenSum = 0;
        for (int j = 0; j < width; j++) {
            int gray = pixels[width * centerY + j];
            redSum += ((gray & 0x00FF0000) >> 16);
            blueSum += ((gray & 0x0000FF00) >> 8);
            greenSum += (gray & 0x000000FF);
        }

        redThresh = (int) (redSum / width * 1.5f * proportion);
        blueThresh = (int) (blueSum / width * 1.5f * proportion);
        greenThresh = (int) (greenSum / width * 1.5f * proportion);
    }

    /**
     * 二值化
     */
    private void binarization(int[] pixels, int width, int height) {
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int gray = pixels[width * i + j];
                pixels[width * i + j] = getColor(gray);
                if (pixels[width * i + j] != PX_WHITE)
                    pixels[width * i + j] = PX_BLACK;
            }
        }

    }

    /**
     * 获取颜色
     */
    private int getColor(int gray) {
        int alpha = 0xFF << 24;
        // 分离三原色
        alpha = ((gray & 0xFF000000) >> 24);
        int red = ((gray & 0x00FF0000) >> 16);
        int green = ((gray & 0x0000FF00) >> 8);
        int blue = (gray & 0x000000FF);
        if (red > redThresh) {
            red = 255;
        } else {
            red = 0;
        }
        if (blue > blueThresh) {
            blue = 255;
        } else {
            blue = 0;
        }
        if (green > greenThresh) {
            green = 255;
        } else {
            green = 0;
        }
        return alpha << 24 | red << 16 | green << 8| blue;

    }


}

