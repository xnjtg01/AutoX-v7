package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Domestic 720x1280 daily-missions adapter.
 *
 * From the user's diagnostic frame_012:
 * the entry opens 成长任务 with 主线任务 and 支线任务 headings;
 * the real 每日任务 tab is at the BOTTOM of the panel, approx (480,1130).
 * Existing Kingshot English Chapter/Daily/Growth tabs were not applicable.
 */
public final class KingshotClaimMissions {
  private final KingshotImageMatcher matcher;
  private final KingshotClaimMail.Screen screen;
  private final KingshotClaimMail.Events events;
  private final KingshotClaimMail.Stopped stopped;
  private final ChineseOcr ocr;
  private final DomesticScreenNavigation navigation;
  private int completed=0;
  public KingshotClaimMissions(KingshotImageMatcher matcher,
       KingshotClaimMail.Screen screen,KingshotClaimMail.Events events,
       KingshotClaimMail.Stopped stopped,ChineseOcr ocr){
    this.matcher=matcher;this.screen=screen;this.events=events;
    this.stopped=stopped;this.ocr=ocr;
    this.navigation=new DomesticScreenNavigation(new DomesticScreenNavigation.IO(){
      public Bitmap screenshot()throws Exception{return screen.screenshot();}
      public void save(Bitmap b,String label)throws Exception{screen.save(b,label);}
      public void event(String level,String module,String message){events.event(level,module,message);}
      public boolean stopped(){return stopped.isStopped();}
    },ocr,matcher);
  }
  private void log(String level,String msg){events.event(level,"每日任务",msg);}
  private int x(Bitmap b,int ref){return Math.round(ref*b.getWidth()/720f);}
  private int y(Bitmap b,int ref){return Math.round(ref*b.getHeight()/1280f);}
  private boolean dailyTabSelected(Bitmap b){
    // In frame_012 growth tab is pale and daily tab is brown. After selecting
    // daily, the right tab should become pale while left becomes brown.
    int a=b.getPixel((int)(b.getWidth()*.68f),(int)(b.getHeight()*.875f));
    return Color.red(a)>205 && Color.green(a)>185&&Color.blue(a)>160;
  }
  private static final class ClaimTarget{
    final int x,y;final String text;
    ClaimTarget(int x,int y,String text){this.x=x;this.y=y;this.text=text;}
  }
  private ClaimTarget locateClaim(Bitmap b)throws Exception{
    ChineseOcr.Result result=ocr.read(b);
    log("INFO","每日任务页面文字："+result.allText);
    for(ChineseOcr.Item item:result.lines){
      if(item.box==null)continue;
      String txt=ChineseOcr.compact(item.text);
      if(!(txt.equals("领取")||txt.equals("全部领取")||txt.equals("一键领取")||
           txt.equals("领取奖励")))continue;
      int cx=item.box.centerX(),cy=item.box.centerY();
      if(cx>=b.getWidth()*.50f&&cx<=b.getWidth()*.98f &&
         cy>=b.getHeight()*.20f&&cy<=b.getHeight()*.88f)
        return new ClaimTarget(cx,cy,txt);
    }
    return null;
  }
  public int run()throws Exception {
    if(stopped.isStopped())return completed;
    if(!navigation.restoreCity()){
      log("SKIP","未回到城镇，不能打开每日任务");return completed;
    }
    Bitmap city=screen.screenshot();
    KingshotImageMatcher.Match scroll;
    try{
      scroll=matcher.findInReferenceArea(city,"claimmissions/missions-scroll.png",
        .75,0,890,220,300);
      log("INFO","成长任务卷轴识别："+scroll);
      if(!scroll.found){log("SKIP","未找到成长任务卷轴");return completed;}
      screen.save(city,"每日任务-进入前");
    }finally{city.recycle();}
    if(stopped.isStopped())return completed;
    RootBridge.tap(scroll.x,scroll.y);
    Thread.sleep(950);
    try{
      Bitmap opened=screen.screenshot();
      try{
        screen.save(opened,"每日任务-成长页");
        if(!navigation.missionPanel(opened)&&
           navigation.detect(opened)!=DomesticScreenNavigation.Page.MISSIONS){
          log("FAIL","点击任务入口后未确认成长任务页面，不继续点击");
          return completed;
        }
        if(!dailyTabSelected(opened)){
          log("INFO","根据真实截图定位底部「每日任务」标签，点击 (480,1132)");
          RootBridge.tap(x(opened,480),y(opened,1132));
        }
      }finally{opened.recycle();}
      Thread.sleep(1000);
      Bitmap daily=screen.screenshot();
      try{
        screen.save(daily,"每日任务-切换后");
        if(!dailyTabSelected(daily)){
          log("FAIL","未检测到右侧每日任务标签被选中的视觉状态；拒绝按内容猜测领取");
          return completed;
        }
      }finally{daily.recycle();}
      for(int i=0;i<4&&!stopped.isStopped();i++){
        Bitmap before=screen.screenshot();
        ClaimTarget target;
        try{
          target=locateClaim(before);
          if(target==null){
            log("INFO",i==0?"未发现可领取的每日任务奖励":"本轮其余奖励已处理");
            break;
          }
          screen.save(before,"每日任务-领取前"+(i+1));
        }finally{before.recycle();}
        if(stopped.isStopped())break;
        log("INFO","点击已确认的中文按钮："+target.text+" "+target.x+","+target.y);
        RootBridge.tap(target.x,target.y);
        Thread.sleep(850);
        Bitmap after=screen.screenshot();
        try{
          screen.save(after,"每日任务-领取后"+(i+1));
          completed++;
          log("INFO","已执行领取点击；游戏奖励是否到账仍需核对");
        }finally{after.recycle();}
      }
      return completed;
    }finally{
      if(!stopped.isStopped()){
        boolean restored=navigation.restoreCity();
        log(restored?"OK":"FAIL",restored?
          "每日任务结束，已关闭成长任务面板，返回城镇":
          "每日任务结束但页面恢复失败，必须停止其他任务");
      }
    }
  }
}
