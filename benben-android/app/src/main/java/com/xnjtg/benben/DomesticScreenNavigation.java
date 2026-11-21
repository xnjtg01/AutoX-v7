package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Domestic Kingshot page recovery, grounded on the user's 2026-11-20
 * 720x1280 diagnostic captures. Never navigates without checking a page first.
 */
public final class DomesticScreenNavigation {
  public enum Page { CITY, MAP, MAIL, MISSIONS, WELCOME, REWARD, UNKNOWN }
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
      if(all.contains("欢迎回来") && (all.contains("离线")||all.contains("收益")))return Page.WELCOME;
      if(all.contains("获得奖励") && (all.contains("点击任意")||all.contains("退出")))return Page.REWARD;
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
    int welcomeClicks=0, missionCloses=0, mailCloses=0, rewardCloses=0, unknownBacks=0;
    for(int attempts=0;attempts<8&&!io.stopped();attempts++) {
      Bitmap b=io.screenshot();
      Page page;
      try {
        page=detect(b);
        note("INFO","当前页面="+page+"，恢复检查="+(attempts+1)+"/8");
        if(page==Page.CITY)return true;
        if(page==Page.MISSIONS){
          if(missionCloses>=2){
            io.save(b,"失败-每日任务关闭两次未成功");
            note("FAIL","已尝试关闭每日任务两次，仍检测到任务面板，停止触摸");
            return false;
          }
          io.save(b,"恢复-发现每日任务弹窗");
          // From user's 720x1280 screenshots, X is at (687,125).
          // The global tap guard MUST hide the floating control first.
          tap(b,687,125);
          missionCloses++;
          note("INFO","已在隐藏浮窗后点击任务面板关闭 x=687 y=125");
        }else if(page==Page.MAIL){
          if(mailCloses>=2){note("FAIL","邮件窗口连续两次关闭失败");return false;}
          io.save(b,"恢复-发现邮件页面");
          tap(b,685,43);
          mailCloses++;
          note("INFO","已在隐藏浮窗后点击邮件页面关闭");
        }else if(page==Page.MAP){
          tap(b,658,1230);
          note("INFO","已点击野外地图右下角城镇入口");
        }else if(page==Page.WELCOME){
          if(welcomeClicks>=2){
            io.save(b,"恢复失败-欢迎回来");
            note("FAIL","欢迎回来弹窗两次确认后仍在，停止点击");
            return false;
          }
          io.save(b,"恢复-欢迎回来确认前");
          // Matches the center of the actual green confirm button (0.50, 0.802).
          tap(b,360,1027);
          welcomeClicks++;
          note("INFO","已点击欢迎回来绿色确定，随后会重新截图验证");
        }else if(page==Page.REWARD){
          if(rewardCloses>=2){note("FAIL","奖励结算画面两次仍未关闭");return false;}
          io.save(b,"恢复-获得奖励弹窗");
          // User's OCR output: 获得奖励 / 点击任意位置退出.
          // Tap a blank part of overlay only while this specific page is detected.
          tap(b,360,960);
          rewardCloses++;
          note("INFO","根据结算画面文字点击空白处退出");
        }else{
          if(unknownBacks<1) {
            RootBridge.back();
            unknownBacks++;
            note("INFO","未知页面仅尝试一次系统返回");
          }else {
            io.save(b,"恢复失败-未知页面");
            note("FAIL","无法判断界面，停止后续任务");
            return false;
          }
        }
      }finally {b.recycle();}
      // Rendering and animation have to settle before verifying each action.
      Thread.sleep(950);
    }
    if(!io.stopped()){
      Bitmap end=io.screenshot();
      try{io.save(end,"恢复失败-最终画面");}finally{end.recycle();}
    }
    note("FAIL","限定次数内未能回到城镇，后续任务停止");
    return false;
  }
}
