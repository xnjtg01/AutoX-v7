package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Domestic Kingshot page recovery, grounded on the user's 2026-11-20
 * 720x1280 diagnostic captures. Never navigates without checking a page first.
 */
public final class DomesticScreenNavigation {
  public enum Page { CITY, MAP, MAIL, MISSIONS, WELCOME, UNKNOWN }
  public interface IO {
    Bitmap screenshot()throws Exception;
    void save(Bitmap bitmap,String label)throws Exception;
    void event(String level,String module,String message);
    boolean stopped();
  }
  private final IO io;
  private final ChineseOcr ocr;
  private final KingshotImageMatcher matcher;
  public DomesticScreenNavigation(IO io,ChineseOcr ocr,KingshotImageMatcher matcher){
    this.io=io;this.ocr=ocr;this.matcher=matcher;
  }
  private int sample(Bitmap b,float x,float y){
    return b.getPixel(Math.max(0,Math.min(b.getWidth()-1,Math.round((b.getWidth()-1)*x))),
      Math.max(0,Math.min(b.getHeight()-1,Math.round((b.getHeight()-1)*y))));
  }
  public boolean missionPanel(Bitmap b) {
    int blue=sample(b,.5f,.157f);
    boolean blueBar=Color.blue(blue)>Color.red(blue)+34 &&
        Color.green(blue)>Color.red(blue)+30;
    int left=sample(b,.25f,.875f),right=sample(b,.68f,.875f);
    boolean leftPale=Color.red(left)>210&&Color.green(left)>200&&Color.blue(left)>185;
    boolean rightPale=Color.red(right)>210&&Color.green(right)>200&&Color.blue(right)>185;
    boolean leftBrown=Color.red(left)>105&&Color.red(left)<185&&Color.green(left)<150;
    boolean rightBrown=Color.red(right)>105&&Color.red(right)<185&&Color.green(right)<150;
    // Growth page has a cyan heading; daily page may change its heading, but
    // retains the inverted two-tab palette at the same fixed bottom positions.
    return (blueBar&&leftPale&&rightBrown)||(rightPale&&leftBrown);
  }
  public boolean mailPanel(Bitmap b) {
    int middle=sample(b,.50f,.175f),top=sample(b,.5f,.03f),bottom=sample(b,.5f,.96f);
    boolean ivory=Color.red(middle)>225&&Color.green(middle)>214&&Color.blue(middle)>198;
    boolean header=Color.red(top)<150&&Color.green(top)<135&&Color.blue(top)<120;
    boolean footer=Color.red(bottom)>120&&Color.red(bottom)<200&&Color.green(bottom)<160;
    return ivory&&header&&footer;
  }
  public Page detect(Bitmap b) {
    if(VisualEngine.welcome(b))return Page.WELCOME;
    if(missionPanel(b))return Page.MISSIONS;
    if(mailPanel(b))return Page.MAIL;
    // OCR is used for page identity only; any unknown text is not a click target.
    try {
      ChineseOcr.Result scanned=ocr.read(b);
      String all=ChineseOcr.compact(scanned.allText);
      if(all.contains("欢迎回来")&&all.contains("离线"))return Page.WELCOME;
      if(all.contains("成长任务")&&(all.contains("主线任务")||all.contains("每日任务")))return Page.MISSIONS;
      if(all.contains("邮件")&&all.contains("联盟")&&all.contains("系统"))return Page.MAIL;
      if(VisualEngine.city(b)){
        String bottom=ChineseOcr.compact(scanned.inArea(
          (int)(b.getWidth()*.76f),(int)(b.getHeight()*.89f),
          (int)(b.getWidth()*.24f),(int)(b.getHeight()*.11f)));
        if(bottom.contains("野外"))return Page.CITY;
        if(bottom.contains("城镇"))return Page.MAP;
      }
    }catch(Exception e){
      io.event("WARN","页面识别","OCR 无法判断页面："+e.getMessage());
    }
    if(VisualEngine.city(b)) {
      try {
        // City reference is the map shortcut displayed at lower-right in city view.
        if(matcher.findInReferenceArea(b,"locator/base-view.png",.60,562,1120,158,160).found)
          return Page.CITY;
        // Wilderness map shows a castle/城镇 shortcut at lower-right.
        if(matcher.findInReferenceArea(b,"locator/map-view.png",.62,562,1120,158,160).found)
          return Page.MAP;
      }catch(Exception e){
        io.event("WARN","页面识别","底栏识别异常："+e.getMessage());
      }
    }
    return Page.UNKNOWN;
  }
  public Page current()throws Exception {
    Bitmap b=io.screenshot();
    try {return detect(b);}
    finally {b.recycle();}
  }
  private void note(String level,String message){io.event(level,"页面恢复",message);}
  private void pause()throws InterruptedException{Thread.sleep(1000);}
  private void tap(Bitmap b,int refX,int refY)throws Exception{
    int x=Math.round(refX*b.getWidth()/720f);
    int y=Math.round(refY*b.getHeight()/1280f);
    RootBridge.tap(x,y);
  }
  public boolean restoreCity()throws Exception{
    for(int attempts=0;attempts<4&&!io.stopped();attempts++) {
      Bitmap b=io.screenshot();
      Page page;
      try {
        page=detect(b);
        note("INFO","当前页面="+page+"，恢复尝试="+(attempts+1)+"/4");
        if(page==Page.CITY)return true;
        if(page==Page.MISSIONS){
          io.save(b,"恢复-发现成长任务弹窗");
          // Source: capture frame_012, top-right close at 515/540,93/960.
          tap(b,685,125);
          note("INFO","已点击成长任务右上角关闭");
        }else if(page==Page.MAIL){
          io.save(b,"恢复-发现邮件页面");
          // Source: capture frame_004, top-right close at 513/540,31/960.
          tap(b,684,42);
          note("INFO","已点击邮件右上角关闭");
        }else if(page==Page.MAP){
          tap(b,658,1230);
          note("INFO","已点击野外地图右下角城镇入口");
        }else if(page==Page.WELCOME){
          // The dedicated welcome dismiss handler must validate this action.
          note("FAIL","仍存在欢迎回来弹窗，不使用通用返回操作");
          io.save(b,"恢复失败-欢迎回来");
          return false;
        }else{
          // A single Android Back is safer than blind coordinate tapping.
          if(attempts==0) {
            RootBridge.back();
            note("INFO","未知页面尝试一次系统返回");
          }else {
            io.save(b,"恢复失败-未知页面");
            note("FAIL","仍不能识别当前页面，停止后续任务");
            return false;
          }
        }
      }finally {b.recycle();}
      pause();
    }
    if(!io.stopped()){
      Bitmap end=io.screenshot();
      try{io.save(end,"恢复失败-最终画面");}finally{end.recycle();}
    }
    note("FAIL","未能在限定次数内返回城镇，后续任务不再执行");
    return false;
  }
}
