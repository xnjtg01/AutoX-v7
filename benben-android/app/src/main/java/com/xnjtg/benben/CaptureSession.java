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
  private final File root;
  private final Listener listener;
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
  public CaptureSession(File root,Listener listener) { this.root=root; this.listener=listener; }
  public synchronized boolean start() {
    if(running) return false;
    running=true;stopRequested=false;successes.clear();failures.clear();welcomeTapCount=0;lastSignature=null;
    sessionDir=new File(root,"capture-session");
    if(!sessionDir.exists()&&!sessionDir.mkdirs()) {
      running=false;listener.onEvent("FAIL","画面采样","无法创建截图目录");return false;
    }
    // Remove old captures so ZIP never accidentally mixes two sessions.
    File[] existing=sessionDir.listFiles();
    if(existing!=null)for(File f:existing)if(f.isFile() && f.getName().startsWith("frame_"))f.delete();
    worker=new Thread(this::captureLoop,"benben-screen-sampler");
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
          Bitmap bmp=RootBridge.screenshot(temporary);
          int w=bmp.getWidth(),h=bmp.getHeight();
          boolean welcome=isWelcomeBackPopup(bmp);
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
              Bitmap after=RootBridge.screenshot(temporary);
              boolean dismissed=!isWelcomeBackPopup(after);
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
      putText(zip,"capture-summary.txt","captured="+successes.size()+"\nfailed="+failures.size()+"\n"+String.join("\n",successes)+"\nFAILURES:\n"+String.join("\n",failures));
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
