package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Limited Android port of KingshotAuto's ClaimMissionsTask.
 * Reuses original open-source image names and processes Chapter, Daily, Growth.
 * Safety improvements over the upstream blind retry loop:
 *  - every action is image-gated;
 *  - no more than one claim-all or three individual claims per tab;
 *  - screenshots after each action for review;
 *  - task stops if the expected missions navigation is not visible;
 *  - back is bounded, and no in-game purchases are performed.
 */
public final class KingshotClaimMissions {
  private final KingshotImageMatcher matcher;
  private final KingshotClaimMail.Screen screen;
  private final KingshotClaimMail.Events events;
  private final KingshotClaimMail.Stopped stopped;
  private final ChineseOcr ocr;
  private int completed=0;
  public KingshotClaimMissions(KingshotImageMatcher matcher,
      KingshotClaimMail.Screen screen,KingshotClaimMail.Events events,
      KingshotClaimMail.Stopped stopped,ChineseOcr ocr){
    this.matcher=matcher;this.screen=screen;this.events=events;this.stopped=stopped;this.ocr=ocr;
  }
  private void log(String level,String message){events.event(level,"每日任务",message);}
  private void pause(int ms)throws InterruptedException{Thread.sleep(ms);}
  private KingshotImageMatcher.Match findMenu(Bitmap b)throws Exception{
    return matcher.findInReferenceArea(b,"claimmissions/missions-scroll.png",.76,0,890,230,330);
  }
  private KingshotImageMatcher.Match findTab(Bitmap b,String file)throws Exception{
    KingshotImageMatcher.Match original=matcher.findInReferenceArea(b,
      "claimmissions/"+file+".png",.77,0,740,720,540);
    if(original.found)return original;
    // Kingshot tab image templates contain English. Domestic server uses Chinese.
    String name=file.equals("chapter")?"章节":file.equals("daily")?"每日":"成长";
    int expectedX=file.equals("chapter")?200:file.equals("daily")?665:435;
    try{
      ChineseOcr.Result scan=ocr.read(b);
      for(ChineseOcr.Item line:scan.lines){
        if(line.box==null||!line.text.contains(name))continue;
        int x=line.box.centerX(),y=line.box.centerY();
        if(Math.abs(x-expectedX)<=105 && y>=750 && y<=1250){
          log("INFO","用国内服 OCR 识别到 "+name+" 标签 x="+x+" y="+y);
          return new KingshotImageMatcher.Match(true,x,y,.92,"OCR:"+name);
        }
      }
    }catch(Exception ex){log("WARN","任务页中文 OCR 失败："+ex.getMessage());}
    return original;
  }
  private KingshotImageMatcher.Match findClaim(Bitmap b,String file)throws Exception{
    KingshotImageMatcher.Match source=matcher.findInReferenceArea(b,
      "claimmissions/"+file+".png",.81,45,150,630,1100);
    if(source.found)return source;
    try{
      ChineseOcr.Result scan=ocr.read(b);
      for(ChineseOcr.Item line:scan.lines){
        if(line.box==null)continue;
        String value=ChineseOcr.compact(line.text);
        boolean target=file.equals("claim-all")?
          (value.contains("全部领取")||value.contains("一键领取")):
          value.equals("领取");
        int x=line.box.centerX(),y=line.box.centerY();
        // Claim-all is normally centered near bottom; individual claims sit on right.
        boolean area=file.equals("claim-all")?
          (x>=200&&x<=670&&y>=730&&y<=1250):
          (x>=350&&x<=700&&y>=200&&y<=1180);
        if(target&&area){
          log("INFO","中文 OCR 识别到领取按钮："+value+" x="+x+" y="+y);
          return new KingshotImageMatcher.Match(true,x,y,.9,"OCR:"+value);
        }
      }
    }catch(Exception ex){log("WARN","领取按钮 OCR 识别失败："+ex.getMessage());}
    return source;
  }
  private boolean changed(Bitmap a,Bitmap b) {
    int count=0,total=0;
    for(int iy=0;iy<15;iy++)for(int ix=0;ix<9;ix++){
      int ax=Math.min(a.getWidth()-1,Math.round((ix+.5f)*a.getWidth()/9));
      int ay=Math.min(a.getHeight()-1,Math.round((iy+.5f)*a.getHeight()/15));
      int bx=Math.min(b.getWidth()-1,Math.round((ix+.5f)*b.getWidth()/9));
      int by=Math.min(b.getHeight()-1,Math.round((iy+.5f)*b.getHeight()/15));
      int x=a.getPixel(ax,ay),y=b.getPixel(bx,by);
      int difference=Math.abs(android.graphics.Color.red(x)-android.graphics.Color.red(y))+
        Math.abs(android.graphics.Color.green(x)-android.graphics.Color.green(y))+
        Math.abs(android.graphics.Color.blue(x)-android.graphics.Color.blue(y));
      if(difference>36)count++;
      total++;
    }
    return count>total*.05;
  }
  public int run()throws Exception{
    if(stopped.isStopped())return completed;
    Bitmap city=screen.screenshot();
    KingshotImageMatcher.Match menu;
    try{
      if(!VisualEngine.city(city)){log("SKIP","未确认城镇主界面，不运行任务领取");return completed;}
      menu=findMenu(city);
      log("INFO","任务卷轴入口："+menu);
      if(!menu.found){log("SKIP","未发现可确认的每日任务入口");return completed;}
      screen.save(city,"任务-进入前");
    }finally{city.recycle();}
    if(stopped.isStopped())return completed;
    RootBridge.tap(menu.x,menu.y);
    pause(1100);
    Bitmap opened=screen.screenshot();
    try{
      screen.save(opened,"任务-面板");
      KingshotImageMatcher.Match daily=findTab(opened,"daily");
      KingshotImageMatcher.Match chapter=findTab(opened,"chapter");
      if(!daily.found&&!chapter.found){
        log("FAIL","打开入口后未找到任务标签，停止，不继续点击");
        return completed;
      }
    }finally{opened.recycle();}
    try{
      String[] labels={"chapter","daily","growth"};
      String[] names={"章节","每日","成长"};
      for(int i=0;i<labels.length && !stopped.isStopped();i++){
        Bitmap pre=screen.screenshot();
        KingshotImageMatcher.Match tab;
        try{tab=findTab(pre,labels[i]);}
        finally{pre.recycle();}
        log("INFO",names[i]+"标签："+tab);
        if(!tab.found)continue;
        RootBridge.tap(tab.x,tab.y);
        pause(700);
        Bitmap tabView=screen.screenshot();
        try{screen.save(tabView,"任务-"+names[i]+"页");}
        finally{tabView.recycle();}
        if(stopped.isStopped())break;
        Bitmap before=screen.screenshot();
        KingshotImageMatcher.Match all;
        try{all=findClaim(before,"claim-all");}
        finally{before.recycle();}
        if(all.found){
          log("INFO",names[i]+"全部领取按钮："+all);
          RootBridge.tap(all.x,all.y);
          pause(900);
          Bitmap after=screen.screenshot();
          try {
            screen.save(after,"任务-"+names[i]+"全部领取后");
            KingshotImageMatcher.Match still=findClaim(after,"claim-all");
            if(!still.found){completed++;log("OK",names[i]+"全部领取按钮消失，疑似成功");}
            else log("WARN",names[i]+"全部领取按钮未消失，不重复点击");
          }finally{after.recycle();}
          continue;
        }
        for(int j=0;j<3&&!stopped.isStopped();j++){
          Bitmap previous=screen.screenshot();
          KingshotImageMatcher.Match claim=findClaim(previous,"claim");
          if(!claim.found){previous.recycle();break;}
          RootBridge.tap(claim.x,claim.y);
          pause(800);
          Bitmap next=screen.screenshot();
          try{
            screen.save(next,"任务-"+names[i]+"单项领取后"+(j+1));
            if(changed(previous,next)){completed++;log("OK",names[i]+"第"+(j+1)+"次领取后画面变化");}
            else{log("WARN",names[i]+"点击后画面没有可靠变化，不再重复点击");break;}
          }finally{previous.recycle();next.recycle();}
        }
      }
    }finally {
      if(!stopped.isStopped()){
        RootBridge.back();
        pause(800);
        Bitmap end=screen.screenshot();
        try{
          screen.save(end,"任务-返回城镇");
          log(VisualEngine.city(end)?"OK":"WARN",
            VisualEngine.city(end)?"已返回城镇":"未确认返回城镇，需要恢复页面");
        }finally{end.recycle();}
      }
    }
    return completed;
  }
}
