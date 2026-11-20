package com.xnjtg.benben;

import android.graphics.Bitmap;

/**
 * Corrected domestic mail flow from user-provided 2026-11-20 screenshots.
 *
 * The domestic UI has five tabs (战争 联盟 系统 报告 收藏) and a persistent
 * bottom-right "一键已读&领取" button. The upstream international script
 * has different tab coordinates and checking if the fixed bottom button
 * disappears gives false negatives. Here we click the global button at most
 * once and do not misrepresent the result as verified reward receipt.
 */
public final class KingshotClaimMail {
  public interface Screen {Bitmap screenshot()throws Exception;void save(Bitmap b,String label)throws Exception;}
  public interface Events {void event(String level,String module,String message);}
  public interface Stopped {boolean isStopped();}
  private final KingshotImageMatcher matcher;
  private final Screen screen;
  private final Events events;
  private final Stopped stopped;
  private final ChineseOcr ocr;
  private final DomesticScreenNavigation navigation;
  public KingshotClaimMail(KingshotImageMatcher matcher,Screen screen,
         Events events,Stopped stopped,ChineseOcr ocr) {
    this.matcher=matcher;this.screen=screen;this.events=events;this.stopped=stopped;this.ocr=ocr;
    this.navigation=new DomesticScreenNavigation(new DomesticScreenNavigation.IO(){
      public Bitmap screenshot()throws Exception{return screen.screenshot();}
      public void save(Bitmap b,String label)throws Exception{screen.save(b,label);}
      public void event(String level,String module,String message){events.event(level,module,message);}
      public boolean stopped(){return stopped.isStopped();}
    },ocr,matcher);
  }
  private void note(String level,String msg){events.event(level,"邮件奖励",msg);}
  public int run()throws Exception{
    if(stopped.isStopped())return 0;
    if(!navigation.restoreCity()){
      note("SKIP","无法恢复城镇主界面，未打开邮件");return 0;
    }
    Bitmap city=screen.screenshot();
    KingshotImageMatcher.Match icon;
    try{
      icon=matcher.findInReferenceArea(city,"claimmail/mail.png",.76,602,974,110,145);
      note("INFO","邮件图标匹配："+icon);
      if(!icon.found){note("SKIP","当前城镇没有匹配到邮件入口");return 0;}
      screen.save(city,"邮件-进入前");
    }finally{city.recycle();}
    if(stopped.isStopped())return 0;
    RootBridge.tap(icon.x,icon.y);
    Thread.sleep(1150);
    try{
      Bitmap opened=screen.screenshot();
      KingshotImageMatcher.Match claimIcon;
      boolean valid;
      boolean textConfirmed=false;
      try{
        screen.save(opened,"邮件-打开后");
        valid=navigation.mailPanel(opened) ||
          navigation.detect(opened)==DomesticScreenNavigation.Page.MAIL;
        if(!valid){note("FAIL","未确认进入国内服邮件页面，拒绝点击领取");return 0;}
        claimIcon=matcher.findInReferenceArea(opened,"claimmail/claim.png",.77,390,1150,260,130);
        try {
          ChineseOcr.Result lines=ocr.read(opened);
          String footer=ChineseOcr.compact(lines.inArea(370,1180,350,100));
          textConfirmed=footer.contains("领取")||footer.contains("已读");
          note("INFO","邮件底部文字："+footer+"，模板："+claimIcon);
        }catch(Exception e){note("WARN","邮件底部 OCR 失败："+e.getMessage());}
        if(!claimIcon.found && !textConfirmed){
          note("SKIP","底部一键领取按钮未识别，不点击");
          return 0;
        }
      }finally{opened.recycle();}
      if(stopped.isStopped())return 0;
      // Reference screenshot frame_004: global right button from x~=420 to 710
      // (screen resolution 720x1280), safe center x=585/y=1240.
      RootBridge.tap(585,1238);
      note("INFO","已点击国内服“一键已读&领取”一次；固定按钮持续可见属正常，不以消失判断成败");
      Thread.sleep(1000);
      Bitmap after=screen.screenshot();
      try{
        screen.save(after,"邮件-一键领取后");
        note("INFO","领取后页面="+navigation.detect(after)+"；实际奖励数量不可由固定按钮推定");
      }finally{after.recycle();}
      return 1;
    }finally{
      if(!stopped.isStopped()){
        boolean restored=navigation.restoreCity();
        note(restored?"OK":"FAIL",restored?"领取流程已返回城镇":"邮件后未能恢复城镇");
      }
    }
  }
}
