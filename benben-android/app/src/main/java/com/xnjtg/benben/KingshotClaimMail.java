package com.xnjtg.benben;

import android.graphics.Bitmap;
import java.io.File;

/**
 * Android adaptation of KingshotAuto/Kingshot-bot's ClaimMailTask.cs:
 *  - detect and open mail icon in 720x1280 reference area;
 *  - process System, Alliance and Battle tabs;
 *  - claim only if the bundled claim.png is actually matched;
 *  - return to base view and verify navigation.
 * Compared with upstream, deliberately removes blind cached-coordinate fallbacks,
 * duplicate taps and unverified in-app purchase/claim actions.
 */
public final class KingshotClaimMail {
  public interface Screen {Bitmap screenshot() throws Exception; void save(Bitmap bitmap,String label)throws Exception;}
  public interface Events {void event(String level,String module,String message);}
  public interface Stopped {boolean isStopped();}
  private final KingshotImageMatcher matcher;
  private final Screen screen;
  private final Events events;
  private final Stopped stopped;
  private int claimed=0;
  public KingshotClaimMail(KingshotImageMatcher matcher,Screen screen,Events events,Stopped stopped){
    this.matcher=matcher;this.screen=screen;this.events=events;this.stopped=stopped;
  }
  private void note(String level,String value){events.event(level,"邮件奖励",value);}
  private void pause(int ms)throws InterruptedException{Thread.sleep(ms);}
  private KingshotImageMatcher.Match findMail(Bitmap screenshot)throws Exception{
    return matcher.findInReferenceArea(screenshot,"claimmail/mail.png",.76,602,974,110,145);
  }
  private KingshotImageMatcher.Match findClaim(Bitmap screenshot)throws Exception{
    return matcher.findInReferenceArea(screenshot,"claimmail/claim.png",.76,360,1145,160,135);
  }
  public int run()throws Exception{
    if(stopped.isStopped())return 0;
    Bitmap initial=screen.screenshot();
    KingshotImageMatcher.Match mail;
    try{
      if(!VisualEngine.city(initial)){
        note("SKIP","未确认在城镇主界面，拒绝使用固定入口");
        return 0;
      }
      mail=findMail(initial);
      note("INFO","邮件图标匹配："+mail);
      if(!mail.found) {
        note("SKIP","城镇未发现 Kingshot 邮件图标，可能国内服外观不同");
        return 0;
      }
      screen.save(initial,"邮件-进入前");
    }finally{initial.recycle();}
    if(stopped.isStopped())return 0;
    RootBridge.tap(mail.x,mail.y);
    pause(1100);
    Bitmap opened=screen.screenshot();
    try {
      screen.save(opened,"邮件-打开后");
      if(VisualEngine.city(opened)) {
        note("FAIL","点击邮件图标后仍是城镇底栏，无法验证邮件页面，停止");
        return 0;
      }
    }finally{opened.recycle();}
    try {
      String[] tabNames={"系统","联盟","战斗"};
      int[][] tabRect={{173,101,108,34},{312,104,98,35},{444,101,96,30}};
      for(int i=0;i<tabNames.length && !stopped.isStopped();i++){
        int[] t=tabRect[i];
        // These coordinates come from KingshotAuto ClaimMailTask.cs, in 720x1280.
        int cx=t[0]+t[2]/2,cy=t[1]+t[3]/2;
        RootBridge.tap(cx,cy);
        pause(650);
        Bitmap tab=screen.screenshot();
        KingshotImageMatcher.Match claim;
        try{
          screen.save(tab,"邮件-"+tabNames[i]+"页");
          claim=findClaim(tab);
          note("INFO",tabNames[i]+"领取按钮匹配："+claim);
        }finally{tab.recycle();}
        if(!claim.found) {
          note("SKIP",tabNames[i]+"页未发现可信的领取按钮");
          continue;
        }
        if(stopped.isStopped())break;
        RootBridge.tap(claim.x,claim.y);
        pause(1050);
        Bitmap after=screen.screenshot();
        try {
          screen.save(after,"邮件-"+tabNames[i]+"领取后");
          KingshotImageMatcher.Match still=findClaim(after);
          if(still.found){
            note("INFO",tabNames[i]+"页领取按钮仍存在，未证明领取成功；不重复点击");
          } else {
            claimed++;
            note("OK",tabNames[i]+"页领取按钮已消失，疑似成功；以游戏结果为准");
          }
        }finally{after.recycle();}
      }
    }finally {
      if(!stopped.isStopped()){
        RootBridge.back();
        pause(800);
        Bitmap end=screen.screenshot();
        try {
          screen.save(end,"邮件-关闭后");
          note(VisualEngine.city(end)?"OK":"WARN",
            VisualEngine.city(end)?"已返回城镇主界面":"返回后未能确认城镇；其他任务应停止");
        }finally{end.recycle();}
      }
    }
    return claimed;
  }
}
