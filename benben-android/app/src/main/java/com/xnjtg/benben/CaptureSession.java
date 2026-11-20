package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
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
  public CaptureSession(File root,Listener listener) { this.root=root; this.listener=listener; }
  public synchronized boolean start() {
    if(running) return false;
    running=true;stopRequested=false;successes.clear();failures.clear();
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
    listener.onEvent("START","画面采样","5 秒后开始，最多 "+MAX_FRAMES+" 帧，每约 4.5 秒一张。请切换到游戏，按需操作各个界面。");
    try {
      for(int count=5;count>0 && !stopRequested;count--)Thread.sleep(1000);
      while(!stopRequested && attempted<MAX_FRAMES) {
        long begun=System.currentTimeMillis();
        File temporary=new File(sessionDir,"working.png");
        File frame=new File(sessionDir,String.format(Locale.US,"frame_%03d.jpg",attempted+1));
        try {
          Bitmap bmp=RootBridge.screenshot(temporary);
          int w=bmp.getWidth(),h=bmp.getHeight();
          Bitmap small=bmp;
          if(w>540)small=Bitmap.createScaledBitmap(bmp,540,Math.round(h*540f/w),true);
          try(FileOutputStream o=new FileOutputStream(frame)){
            if(!small.compress(CompressFormat.JPEG,75,o))throw new Exception("JPEG 压缩失败");
          } finally {
            if(small!=bmp)small.recycle();
            bmp.recycle();
          }
          String line=frame.getName()+","+w+"x"+h+","+frame.length()+" bytes";
          successes.add(line);
          listener.onEvent("OK","画面采样","第 "+(attempted+1)+"/"+MAX_FRAMES+" 帧："+line);
        } catch(Exception e) {
          String message="第 "+(attempted+1)+" 帧："+e.getClass().getSimpleName()+" "+e.getMessage();
          failures.add(message);
          listener.onEvent("FAIL","画面采样",message);
        } finally { temporary.delete(); }
        attempted++;
        if(attempted<MAX_FRAMES && !stopRequested) {
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
