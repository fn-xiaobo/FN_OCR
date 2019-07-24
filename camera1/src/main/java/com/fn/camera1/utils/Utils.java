package com.fn.camera1.utils;

import android.graphics.Bitmap;

/**
 * Created by 77167 on 2018/10/11.
 */

public class Utils {


    private boolean isOutOfBorder;

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
        boolean isOutOfRect= false;


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
                        isOutOfRect= false;
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
                            isOutOfRect= true;
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


}
