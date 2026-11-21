package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Explicitly-started screenshot sampling session; never controls the game.
 * Keeps images in the app's local external-files directory until the user exports them.
 */
public final class CaptureSession {
  public interface Listener { void onEvent(String level,String component,String message); }
  public interface ScreenshotGuard { void before() throws Exception; void after() throws Exception; }
  private final File root;
  private final Listener listener;
  private final ScreenshotGuard guard;
  private final KingshotImageMatcher matcher;
  private final ChineseOcr chineseOcr=new ChineseOcr();
  private volatile boolean running=false;
  private volatile boolean stopRequested=false;
  private volatile Thread worker;
  private File sessionDir;
  private final List<String> successes=new ArrayList<>();
  private final List<String> failures=new ArrayList<>();
  private static final int MAX_FRAMES=20;
  private static final long INTERVAL_MS=4500L;
  private static final long MAX_DURATION_MS=180000L;
  private int welcomeTapCount=0;
  private long[] lastSignature=null;
  public CaptureSession(File root,Listener listener,ScreenshotGuard guard,KingshotImageMatcher matcher) { this.root=root; this.listener=listener; this.guard=guard; this.matcher=matcher; }
  private Bitmap cleanScreenshot(File output) throws Exception {
    guard.before();
    try {return RootBridge.screenshot(output);}
    finally {guard.after();}
  }
  public synchronized boolean start() { return startMode(0); }
  public synchronized boolean startTour() { return startMode(1); }
  public synchronized boolean startAutoHelp() { return startMode(2); }
  public synchronized boolean startRewards() { return startMode(3); }
  public synchronized boolean startMarch(int kind) {
    if(kind<1||kind>4)return false;
    return startMode(3+kind);
  }
  public synchronized boolean startAllTasks() {return startMode(8);}
  private synchronized boolean startMode(int mode) {
    if(running) return false;
    running=true;stopRequested=false;successes.clear();failures.clear();welcomeTapCount=0;lastSignature=null;
    // Every run gets its own directory: never overwrite earlier evidence while
    // logs persist across app restarts. (Previously frame_001 files were recycled.)
    sessionDir=new File(new File(root,"diagnostic-sessions"),
      "session-"+System.currentTimeMillis()+"-"+mode);
    if(!sessionDir.exists()&&!sessionDir.mkdirs()) {
      running=false;listener.onEvent("FAIL","画面采样","无法创建本次独立截图目录");return false;
    }
    listener.onEvent("INFO","诊断存档","新会话目录："+sessionDir.getName());
    worker=new Thread(()->{
      if(mode==0)captureLoop();
      else if(mode==1)tourLoop();
      else if(mode==2)autoHelpLoop();
      else if(mode==3)rewardLoop();
      else if(mode==8)allTasksLoop();
      else marchLoop(mode-3);
    },"benben-game-task");
    worker.start();
    return true;
  }
  private void captureLoop() {
    int attempted=0;
    long started=System.currentTimeMillis();
    listener.onEvent("START","画面采样","5 秒后开始，最多 "+MAX_FRAMES+" 张不同画面，最长3分钟。只在确定识别欢迎回来弹窗时自动点一次“确定”。");
    try {
      for(int count=5;count>0 && !stopRequested;count--)Thread.sleep(1000);
      while(!stopRequested && successes.size()<MAX_FRAMES && System.currentTimeMillis()-started<MAX_DURATION_MS) {
        long begun=System.currentTimeMillis();
        File temporary=new File(sessionDir,"working.png");
        File frame=new File(sessionDir,String.format(Locale.US,"frame_%03d.jpg",successes.size()+1));
        try {
          Bitmap bmp=cleanScreenshot(temporary);
          int w=bmp.getWidth(),h=bmp.getHeight();
          boolean welcome=detectWelcome(bmp);
          long[] sig=signature(bmp);
          boolean different=!similar(sig,lastSignature);
          if(different) {
            Bitmap small=bmp;
            try {
              if(w>540)small=Bitmap.createScaledBitmap(bmp,540,Math.round(h*540f/w),true);
              try(FileOutputStream o=new FileOutputStream(frame)){
                if(!small.compress(CompressFormat.JPEG,75,o))throw new Exception("JPEG 压缩失败");
              }
            } finally {if(small!=bmp)small.recycle();}
            lastSignature=sig;
            String line=frame.getName()+","+w+"x"+h+","+frame.length()+" bytes";
            successes.add(line);
            listener.onEvent("OK","画面采样","有效画面 "+successes.size()+"/"+MAX_FRAMES+"："+line);
          } else if(attempted%4==0) listener.onEvent("INFO","画面采样","画面没有明显变化，已跳过重复截图；继续等待你操作游戏");
          bmp.recycle();
          if(welcome) {
            if(welcomeTapCount==0) {
              welcomeTapCount++;
              int tx=Math.round(w*0.5f),ty=Math.round(h*0.8f);
              listener.onEvent("INFO","欢迎回来弹窗","高置信度检测到弹窗，准备点击“确定”，坐标 "+tx+","+ty);
              RootBridge.tap(tx,ty);
              Thread.sleep(1250);
              Bitmap after=cleanScreenshot(temporary);
              boolean dismissed=!detectWelcome(after);
              after.recycle();
              if(dismissed)listener.onEvent("OK","欢迎回来弹窗","已自动点击确定，弹窗消失");
              else listener.onEvent("FAIL","欢迎回来弹窗","点击后弹窗仍存在；已停止自动点击，请手动关闭");
            } else if(attempted%4==0) listener.onEvent("INFO","欢迎回来弹窗","已尝试关闭一次，不重复点击，请手动处理");
          }
        } catch(Exception e) {
          String message="第 "+(attempted+1)+" 帧："+e.getClass().getSimpleName()+" "+e.getMessage();
          failures.add(message);
          listener.onEvent("FAIL","画面采样",message);
        } finally { temporary.delete(); }
        attempted++;
        if(successes.size()<MAX_FRAMES && !stopRequested) {
          long sleep=Math.max(0,INTERVAL_MS-(System.currentTimeMillis()-begun));
          Thread.sleep(sleep);
        }
      }
    }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    finally {
      running=false;
      listener.onEvent("DONE","画面采样","采集结束。成功 "+successes.size()+" 帧，失败 "+failures.size()+" 帧。请返回助手点击“导出完整诊断 ZIP”。");
    }
  }


  /** Prefer the screenshot's stable visual features, with domestic Chinese OCR fallback. */
  private boolean detectWelcome(Bitmap screenshot){
    if(VisualEngine.welcome(screenshot))return true;
    try {
      String text=ChineseOcr.compact(chineseOcr.read(screenshot).allText);
      return text.contains("欢迎回来") && (text.contains("离线")||text.contains("确定"));
    }catch(Exception e){
      listener.onEvent("WARN","弹窗OCR","无法读取文字，保留颜色匹配判断："+e.getMessage());
      return false;
    }
  }
  private boolean dismissWelcome(Bitmap first,File temp) throws Exception {
    if(!detectWelcome(first))return true;
    listener.onEvent("INFO","弹窗处理","检测到“欢迎回来／离线收益”，用 Kingshot 原坐标策略和国内服按钮位置尝试关闭");
    // Source: GameCoordinates.WelcomeBackDismiss=(370,1011) at 720x1280.
    // Domestic screenshot green confirm center approximately x=360, y=1025.
    int x=VisualEngine.confirmX(first),y=VisualEngine.confirmY(first);
    for(int attempt=1; attempt<=2 && !stopRequested; attempt++) {
      listener.onEvent("INFO","弹窗处理","安全点击确认 "+attempt+"/2，屏幕坐标 "+x+","+y);
      RootBridge.tap(x,y);
      Thread.sleep(1300);
      Bitmap after=cleanScreenshot(temp);
      try {
        if(!detectWelcome(after)) {
          listener.onEvent("OK","弹窗处理","已验证欢迎弹窗不再存在");
          return true;
        }
      } finally {after.recycle();}
    }
    listener.onEvent("FAIL","弹窗处理","欢迎弹窗仍未关闭，停止自动点击并保留诊断截图");
    Bitmap failed=cleanScreenshot(temp);
    try {saveTourFrame(failed,"欢迎回来弹窗-关闭失败");}
    finally {failed.recycle();}
    return false;
  }
  /** One real, tightly scoped task: optional welcome dialog + one quick-help tap.
   * Will not tap unless city navigation and reference icon both match.
   */
  private void autoHelpLoop() {
    File temp=new File(sessionDir,"autotask-working.png");
    listener.onEvent("START","联盟互助","5秒后进入游戏：关闭欢迎回来弹窗，并在识别到绿色握手快捷按钮时点击一次。");
    try {
      for(int i=5;i>0&&!stopRequested;i--)Thread.sleep(1000);
      if(stopRequested)return;
      Bitmap frame=cleanScreenshot(temp);
      try{
        saveTourFrame(frame,"任务开始画面");
        if(!dismissWelcome(frame,temp))return;
      }finally{frame.recycle();}
      if(stopRequested)return;
      Bitmap current=cleanScreenshot(temp);
      try {
        VisualEngine.Observation observation=VisualEngine.inspect(current);
        listener.onEvent("INFO","联盟互助","画面判断："+observation);
        saveTourFrame(current,"弹窗处理后画面");
        if(!observation.city){
          listener.onEvent("FAIL","联盟互助","无法确认城镇底栏，未执行互助点击");
          return;
        }
        KingshotImageMatcher.Match help=matcher.find(current,"alliance/alliance-help.png",.80);
        listener.onEvent("INFO","联盟互助","来自 Kingshot 开源素材的模板匹配："+help);
        if(!help.found){
          listener.onEvent("INFO","联盟互助","图标匹配未达阈值，可能没有可帮助请求；未点击");
          return;
        }
        listener.onEvent("INFO","联盟互助","使用图像匹配位置执行单次点击："+help.x+","+help.y);
        RootBridge.tap(help.x,help.y);
      }finally{current.recycle();}
      Thread.sleep(1400);
      if(stopRequested)return;
      Bitmap after=cleanScreenshot(temp);
      try {
        saveTourFrame(after,"互助点击后画面");
        KingshotImageMatcher.Match post=matcher.find(after,"alliance/alliance-help.png",.80);
        listener.onEvent(post.found?"INFO":"OK","联盟互助","点击后图标匹配："+post+"；仅代表图像状态变化，无法单独证明游戏任务成功");
      }finally{after.recycle();}
    }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){failures.add("联盟互助："+e);listener.onEvent("FAIL","联盟互助",e.toString());}
    finally {
      running=false;temp.delete();
      listener.onEvent("DONE","联盟互助","已结束，截图 "+successes.size()+" 张；可导出 ZIP 核对真实效果。");
    }
  }

  /**
   * Kingshot source-driven rewards: start with ClaimMailTask's System/Alliance/Battle
   * tabs. All clicks except confirmed welcome popup require actual matching imagery.
   */
  private void rewardLoop() {
    File temp=new File(sessionDir,"reward-working.png");
    listener.onEvent("START","奖励领取","5秒后切换游戏：运行 Kingshot 邮件+每日任务领取流程，缺少可靠图片时跳过");
    try {
      for(int i=5;i>0&&!stopRequested;i--)Thread.sleep(1000);
      if(stopRequested)return;
      Bitmap initial=cleanScreenshot(temp);
      try{
        saveTourFrame(initial,"邮件奖励启动画面");
        if(!dismissWelcome(initial,temp))return;
      }finally{initial.recycle();}
      if(stopRequested)return;
      KingshotClaimMail mail=new KingshotClaimMail(matcher,
        new KingshotClaimMail.Screen(){
          @Override public Bitmap screenshot()throws Exception{return cleanScreenshot(temp);}
          @Override public void save(Bitmap bitmap,String label)throws Exception{saveTourFrame(bitmap,label);}
        },
        (level,module,message)->listener.onEvent(level,module,message),
        ()->stopRequested,chineseOcr
      );
      int claims=mail.run();
      listener.onEvent("INFO","奖励领取","邮件模块：观察到领取按钮消失 "+claims+" 次");
      if(!stopRequested){
        KingshotClaimMissions missions=new KingshotClaimMissions(matcher,
          new KingshotClaimMail.Screen(){
            @Override public Bitmap screenshot()throws Exception{return cleanScreenshot(temp);}
            @Override public void save(Bitmap bitmap,String label)throws Exception{saveTourFrame(bitmap,label);}
          },
          (level,module,message)->listener.onEvent(level,module,message),
          ()->stopRequested,chineseOcr);
        int missionsClaimed=missions.run();
        listener.onEvent("DONE","奖励领取","邮件疑似领取 "+claims+" 次，任务疑似领取 "+missionsClaimed+" 次；仅按按钮变化判断，需要游戏验证");
      }
    }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){failures.add("邮件奖励："+e);listener.onEvent("FAIL","邮件奖励",e.toString());}
    finally {
      temp.delete();running=false;
      listener.onEvent("DONE","奖励领取","已结束，截图 "+successes.size()+" 张，状态和证据可在 ZIP 中检查");
    }
  }


  private DomesticScreenNavigation navigation(final File screenshotFile){
    return new DomesticScreenNavigation(new DomesticScreenNavigation.IO(){
      public Bitmap screenshot()throws Exception{return cleanScreenshot(screenshotFile);}
      public void save(Bitmap b,String name)throws Exception{saveTourFrame(b,name);}
      public void event(String level,String module,String message){listener.onEvent(level,module,message);}
      public boolean stopped(){return stopRequested;}
    },chineseOcr,matcher);
  }
  private KingshotMarchTasks marchEngine(final File screenshotFile){
    return new KingshotMarchTasks(matcher,new KingshotMarchTasks.IO(){
      @Override public Bitmap screenshot()throws Exception{return cleanScreenshot(screenshotFile);}
      @Override public void save(Bitmap bitmap,String label)throws Exception{saveTourFrame(bitmap,label);}
      @Override public void event(String level,String module,String message){
        listener.onEvent(level,module,message);
      }
      @Override public boolean stopped(){return stopRequested;}
    },chineseOcr);
  }
  private void marchLoop(int task){
    final String[] types={"资源采集","加入集结","打野怪","训练士兵"};
    String title=types[Math.max(0,Math.min(3,task-1))];
    File temp=new File(sessionDir,"march-working.png");
    listener.onEvent("START",title,"5秒后在游戏中开始。采用 Kingshot 对应模板和状态验证，最多执行一次");
    try{
      for(int i=0;i<5&&!stopRequested;i++)Thread.sleep(1000);
      if(stopRequested)return;
      Bitmap frame=cleanScreenshot(temp);
      try{
        saveTourFrame(frame,title+"起始画面");
        if(!dismissWelcome(frame,temp))return;
      }finally{frame.recycle();}
      if(stopRequested)return;
      boolean ok=marchEngine(temp).run(task);
      listener.onEvent(ok?"OK":"SKIP",title,ok?"一次任务流程已执行，结果仍需在游戏内核对":"识别不足或队列条件不满足，已停止");
    }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){failures.add(title+":"+e);listener.onEvent("FAIL",title,e.toString());}
    finally{running=false;temp.delete();listener.onEvent("DONE",title,"已结束，截图 "+successes.size()+" 张，可导出诊断 ZIP");}
  }
  private void allTasksLoop() {
    File temp=new File(sessionDir,"all-working.png");
    listener.onEvent("START","七项巡检","5 秒后运行：奖励领取、联盟帮助、训练、集结、采集、打野；每项均有图像校验和失败保护。");
    try{
      for(int i=0;i<5&&!stopRequested;i++)Thread.sleep(1000);
      if(stopRequested)return;
      Bitmap first=cleanScreenshot(temp);
      try{
        saveTourFrame(first,"一键任务起始画面");
        if(!dismissWelcome(first,temp))return;
      }finally{first.recycle();}
      if(stopRequested)return;
      KingshotClaimMail.Screen screen=new KingshotClaimMail.Screen(){
        @Override public Bitmap screenshot()throws Exception{return cleanScreenshot(temp);}
        @Override public void save(Bitmap bitmap,String label)throws Exception{saveTourFrame(bitmap,label);}
      };
      KingshotClaimMail.Stopped stopped=()->stopRequested;
      KingshotClaimMail.Events events=(level,module,message)->listener.onEvent(level,module,message);
      // Every module first restores and re-verifies the domestic city screen.
      DomesticScreenNavigation nav=navigation(temp);
      try{
        new KingshotClaimMail(matcher,screen,events,stopped,chineseOcr).run();
      }catch(Exception e){failures.add("邮件："+e);listener.onEvent("FAIL","邮件奖励",e.toString());}
      if(!stopRequested && !nav.restoreCity()){
        listener.onEvent("FAIL","七项巡检","邮件任务后未能恢复城镇，后续任务停止");
        return;
      }
      if(!stopRequested){
        try{new KingshotClaimMissions(matcher,screen,events,stopped,chineseOcr).run();}
        catch(Exception e){failures.add("任务："+e);listener.onEvent("FAIL","每日任务",e.toString());}
      }
      if(!stopRequested && !nav.restoreCity()){
        listener.onEvent("FAIL","七项巡检","每日任务后仍处于非城镇界面，停止后续任务");
        return;
      }
      if(!stopRequested){
        Bitmap pre=cleanScreenshot(temp);
        try{
          KingshotImageMatcher.Match help=matcher.find(pre,"alliance/alliance-help.png",.80);
          listener.onEvent("INFO","联盟帮助","已检测："+help);
          if(VisualEngine.city(pre)&&help.found){
            RootBridge.tap(help.x,help.y);
            Thread.sleep(900);
            Bitmap after=cleanScreenshot(temp);
            try{saveTourFrame(after,"一键任务-联盟帮助之后");}
            finally{after.recycle();}
            listener.onEvent("INFO","联盟帮助","已执行单次帮助点击，是否成功需看游戏状态");
          }
        }catch(Exception e){failures.add("互助："+e);listener.onEvent("FAIL","联盟帮助",e.toString());}
        finally{pre.recycle();}
      }
      KingshotMarchTasks engine=marchEngine(temp);
      // Source-derived one-cycle ordering: base-view operations before map-view ones.
      int[] tasks={KingshotMarchTasks.TRAIN,KingshotMarchTasks.RALLY,
        KingshotMarchTasks.FARM,KingshotMarchTasks.HUNT};
      String[] names={"训练士兵","加入集结","资源采集","自动打野怪"};
      for(int i=0;i<tasks.length&&!stopRequested;i++){
        try{
          if(!nav.restoreCity()){
            listener.onEvent("FAIL","七项巡检",names[i]+"执行前未能返回城镇，中断任务队列");
            break;
          }
          boolean ok=engine.run(tasks[i]);
          listener.onEvent(ok?"OK":"SKIP",names[i],ok?"已执行单次源代码移植操作，等待游戏结果核实":"不满足可靠识别条件，已跳过");
        }catch(Exception e){
          failures.add(names[i]+":"+e);
          listener.onEvent("FAIL",names[i],e.toString());
        }
      }
    }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){failures.add("全量任务："+e);listener.onEvent("FAIL","一键任务",e.toString());}
    finally{
      running=false;temp.delete();
      listener.onEvent("DONE","七项巡检","本轮完成，自动点击仅限匹配成功的页面；成功项需在游戏里验证。已保存 "+successes.size()+" 张截图");
    }
  }
  /** Known, low-risk navigation only: city -> alliance -> back -> wilderness -> back.
   * No recruitment, reward collection, resource dispatch, combat or account actions.
   */
  private void tourLoop() {
    listener.onEvent("START","自动巡检","5秒后开始，仅自动进入联盟和野外入口。不会领取、购买或派兵。可随时点浮窗停止。");
    File temporary=new File(sessionDir,"tour-working.png");
    try {
      for(int i=0;i<5&&!stopRequested;i++)Thread.sleep(1000);
      if(stopRequested)return;
      Bitmap city=cleanScreenshot(temporary);
      try {
        saveTourFrame(city,"进入游戏画面");
        if(!dismissWelcome(city,temporary))return;
      } finally {city.recycle();}
      Bitmap cityReady=cleanScreenshot(temporary);
      try {
        if(!VisualEngine.city(cityReady)) {
          saveTourFrame(cityReady,"未识别城镇");
          listener.onEvent("FAIL","自动巡检","未识别到城镇底部菜单，安全停止。请检查当前是否有其他弹窗。");
          return;
        }
      } finally {cityReady.recycle();}
      String[] names={"联盟","野外"};
      float[] xs={.732f,.913f};
      for(int i=0;i<names.length && !stopRequested;i++){
        Bitmap verify=cleanScreenshot(temporary);
        boolean valid;
        int width=verify.getWidth(),height=verify.getHeight();
        try{ valid=VisualEngine.city(verify); }
        finally{verify.recycle();}
        if(!valid){
          listener.onEvent("FAIL","自动巡检","进入"+names[i]+"前未确认城镇菜单，停止后续点击");
          return;
        }
        int x=Math.round(width*xs[i]), y=Math.round(height*.959f);
        listener.onEvent("INFO","自动巡检","正在打开"+names[i]+"，目标坐标："+x+","+y);
        RootBridge.tap(x,y);
        Thread.sleep(2000);
        if(stopRequested)return;
        Bitmap opened=cleanScreenshot(temporary);
        try{
          saveTourFrame(opened,names[i]+"页面");
          listener.onEvent("OK","自动巡检","已记录"+names[i]+"的画面，等待后续页面识别验证");
        }finally{opened.recycle();}
        if(stopRequested)return;
        RootBridge.back();
        Thread.sleep(1500);
      }
      listener.onEvent("DONE","自动巡检","城镇→联盟→野外安全导航已执行。请导出诊断 ZIP 以核对真实页面是否正确。");
    } catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){failures.add("自动巡检："+e);listener.onEvent("FAIL","自动巡检",e.toString());}
    finally {
      temporary.delete();
      running=false;
      listener.onEvent("DONE","自动巡检","已结束，保存 "+successes.size()+" 张截图，可回到助手导出 ZIP。");
    }
  }
  private static boolean hasBottomNavigation(Bitmap bmp){
    // Verify the pale beige fixed-bottom toolbar observed in the user's 540x960
    // city screenshot. Abort if overlay/dialog or another app hides the toolbar.
    int hits=0;
    for(float x:new float[]{.02f,.19f,.36f,.53f,.70f,.97f}){
      int px=Math.min(bmp.getWidth()-1,Math.round(bmp.getWidth()*x));
      int py=Math.min(bmp.getHeight()-1,Math.round(bmp.getHeight()*.984f));
      int c=bmp.getPixel(px,py);
      int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
      if(r>=140&&r<=225&&g>=125&&g<=200&&b>=95&&b<=175&&r>g&&g>b)hits++;
    }
    return hits>=5;
  }
  private void saveTourFrame(Bitmap bitmap,String step)throws Exception{
    File frame=new File(sessionDir,String.format(Locale.US,"frame_%03d.jpg",successes.size()+1));
    Bitmap reduced=bitmap;
    if(bitmap.getWidth()>540)reduced=Bitmap.createScaledBitmap(bitmap,540,Math.round(bitmap.getHeight()*540f/bitmap.getWidth()),true);
    try(FileOutputStream out=new FileOutputStream(frame)){
      if(!reduced.compress(CompressFormat.JPEG,78,out))throw new Exception("截图编码失败");
    }finally{if(reduced!=bitmap)reduced.recycle();}
    successes.add(frame.getName()+","+bitmap.getWidth()+"x"+bitmap.getHeight()+","+step);
    listener.onEvent("OK","自动巡检",step+" 已截图，第 "+successes.size()+" 张");
  }
  // Reference image: user's 329x585 welcome-back dialog screenshot.
  // A conservative compound check: beige center panel + distinctive green button.
  private static boolean isWelcomeBackPopup(Bitmap bmp) {
    int w=bmp.getWidth(), h=bmp.getHeight();
    int checks=0;
    float[][] panel={{0.15f,0.34f},{0.85f,0.34f},{0.18f,0.63f},{0.80f,0.73f}};
    for(float[] p:panel){
      int c=bmp.getPixel(Math.round((w-1)*p[0]),Math.round((h-1)*p[1]));
      int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
      if(r>=175 && r<=249 && g>=155 && g<=241 && b>=123 && b<=227 && r>=g && g>=b) checks++;
    }
    if(checks<3)return false;
    int green=0;
    float[][] button={{0.34f,0.787f},{0.43f,0.787f},{0.57f,0.787f},{0.65f,0.787f},
      {0.34f,0.817f},{0.43f,0.817f},{0.57f,0.817f},{0.65f,0.817f}};
    for(float[] p:button){
      int c=bmp.getPixel(Math.round((w-1)*p[0]),Math.round((h-1)*p[1]));
      int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
      if(g>=140 && g>r+35 && g>b+32 && r<=160 && b<=150)green++;
    }
    int outside=bmp.getPixel(Math.round(w*.25f),Math.round(h*.8f));
    boolean paleOutside=Color.red(outside)>190 && Color.green(outside)>180 &&
      Math.abs(Color.red(outside)-Color.green(outside))<35;
    return green>=7 && paleOutside;
  }
  private static long[] signature(Bitmap bitmap){
    final int cols=16,rows=24;
    long[] result=new long[cols*rows];
    for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
      int px=Math.min(bitmap.getWidth()-1,Math.round((x+.5f)*bitmap.getWidth()/cols));
      int py=Math.min(bitmap.getHeight()-1,Math.round((y+.5f)*bitmap.getHeight()/rows));
      int c=bitmap.getPixel(px,py);
      result[y*cols+x]=((Color.red(c)/24L)<<16)|((Color.green(c)/24L)<<8)|(Color.blue(c)/24L);
    }
    return result;
  }
  private static boolean similar(long[] a,long[] b) {
    if(a==null||b==null||a.length!=b.length)return false;
    int changed=0;
    for(int i=0;i<a.length;i++) {
      long x=a[i],y=b[i];
      int dr=(int)Math.abs(((x>>16)&255)-((y>>16)&255));
      int dg=(int)Math.abs(((x>>8)&255)-((y>>8)&255));
      int db=(int)Math.abs((x&255)-(y&255));
      if(dr+dg+db>=5)changed++;
    }
    return changed<a.length*.12;
  }
  public boolean isRunning(){return running;}
  public void stop(){stopRequested=true;}
  public int frames(){return successes.size();}
  public synchronized void export(OutputStream destination,String report) throws Exception {
    if(running)throw new IllegalStateException("请先结束采样再导出");
    if(sessionDir==null)throw new IllegalStateException("还没有运行过画面采样");
    try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(destination))){
      putText(zip,"README.txt","奔奔王国助手集中测试包\n本地保存，未经手动导出不上传。\n截图为 JPEG 540px 宽，时间顺序排列。\n请在分享前检查画面是否包含隐私内容。\n");
      putText(zip,"diagnostic-report.txt",report);
      putText(zip,"capture-summary.txt","session="+sessionDir.getName()+"\n"+"captured="+successes.size()+"\nfailed="+failures.size()+"\n"+String.join("\n",successes)+"\nFAILURES:\n"+String.join("\n",failures));
      File[] entries=sessionDir.listFiles();
      if(entries!=null)for(File entry:entries){
        if(!entry.isFile()||!entry.getName().matches("frame_[0-9]{3}\\.jpg"))continue;
        zip.putNextEntry(new ZipEntry("screenshots/"+entry.getName()));
        try(FileInputStream in=new FileInputStream(entry)){
          byte[] b=new byte[16384];int n;
          while((n=in.read(b))!=-1)zip.write(b,0,n);
        }
        zip.closeEntry();
      }
    }
  }
  private static void putText(ZipOutputStream zip,String name,String data)throws Exception{
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data.getBytes("UTF-8"));
    zip.closeEntry();
  }
}
