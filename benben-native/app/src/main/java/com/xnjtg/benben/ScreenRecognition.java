package com.xnjtg.benben;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.os.Build;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

/** Original screenshot OCR and local computer-vision primitives. */
public final class ScreenRecognition {
    public interface Callback { void onResult(String result); }
    private ScreenRecognition(){}
    public static void captureAndRead(AccessibilityService service, Callback callback) {
        if (Build.VERSION.SDK_INT < 30) { callback.onResult("截图 OCR 需要 Android 11+"); return; }
        service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
            service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult image) {
                    Bitmap bmp = null;
                    try {
                        android.hardware.HardwareBuffer buffer=image.getHardwareBuffer();
                        Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,image.getColorSpace());
                        if(hardware==null){buffer.close();callback.onResult("无法解码截图");return;}
                        bmp=hardware.copy(Bitmap.Config.ARGB_8888,false);
                        buffer.close();
                        if(bmp==null){callback.onResult("无法复制截图");return;}
                        Bitmap finalBitmap=bmp;
                        TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build())
                          .process(InputImage.fromBitmap(finalBitmap,0))
                          .addOnSuccessListener(rec->{
                            StringBuilder sb=new StringBuilder();
                            for(com.google.mlkit.vision.text.Text.TextBlock block:rec.getTextBlocks()) {
                                sb.append(block.getText()).append(" | ");
                            }
                            callback.onResult(sb.length()==0?"画面未识别出文字":sb.toString());
                            finalBitmap.recycle();
                          })
                          .addOnFailureListener(e->{callback.onResult("OCR 失败: "+e.getMessage());finalBitmap.recycle();});
                    }catch(Exception e){if(bmp!=null)bmp.recycle();callback.onResult("截图失败: "+e.getMessage());}
                }
                @Override public void onFailure(int errorCode){callback.onResult("截图服务失败: "+errorCode);}
            });
    }
    /** Template-matching foundation: returns x,y,score, performs no game actions. */
    public static double[] matchTemplate(Bitmap screenshot,Bitmap template) {
        Mat src=new Mat(),tmp=new Mat(),result=new Mat();
        try {
            Utils.bitmapToMat(screenshot,src); Utils.bitmapToMat(template,tmp);
            Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.cvtColor(tmp,tmp,Imgproc.COLOR_RGBA2GRAY);
            if(src.cols()<tmp.cols()||src.rows()<tmp.rows())return new double[]{-1,-1,0};
            Imgproc.matchTemplate(src,tmp,result,Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult point=Core.minMaxLoc(result);
            return new double[]{point.maxLoc.x+tmp.cols()/2.0,point.maxLoc.y+tmp.rows()/2.0,point.maxVal};
        }finally{src.release();tmp.release();result.release();}
    }
}
