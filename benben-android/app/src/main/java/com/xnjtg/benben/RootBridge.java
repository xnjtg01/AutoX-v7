package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public final class RootBridge {
  private RootBridge() {}
  public interface TouchGuard {
    void before() throws Exception;
    void after() throws Exception;
  }
  private static volatile TouchGuard touchGuard;
  public static void setTouchGuard(TouchGuard guard){touchGuard=guard;}

  private static Process start(String command) throws Exception {
    return new ProcessBuilder("su","-c",command).redirectErrorStream(false).start();
  }
  public static String checkRoot() throws Exception {
    Process p = start("id");
    ByteArrayOutputStream out = read(p.getInputStream(), 2048);
    if (!p.waitFor(8, TimeUnit.SECONDS)) {p.destroyForcibly(); throw new Exception("Root 检测超时");}
    String s = out.toString("UTF-8").trim();
    if (p.exitValue()!=0 || !s.contains("uid=0")) throw new Exception("未取得 Root: "+s);
    return s;
  }
  private static ByteArrayOutputStream read(InputStream in,int limit) throws Exception {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    byte[] b=new byte[8192]; int n;
    while((n=in.read(b))!=-1){ if(bytes.size()+n>limit)throw new Exception("截图数据超出上限");bytes.write(b,0,n);}
    return bytes;
  }
  public static Bitmap screenshot(File destination) throws Exception {
    Process p=start("screencap -p");
    ByteArrayOutputStream bytes;
    try {bytes=read(p.getInputStream(),16*1024*1024);}
    catch(Exception e){p.destroyForcibly(); throw e;}
    if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("截图超时");}
    if(p.exitValue()!=0)throw new Exception("screencap 失败，退出码："+p.exitValue());
    byte[] png=bytes.toByteArray();
    Bitmap bmp=BitmapFactory.decodeByteArray(png,0,png.length);
    if(bmp==null)throw new Exception("无法解析 PNG 截图");
    File parent=destination.getParentFile();
    if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new Exception("无法创建截图目录");
    try(FileOutputStream o=new FileOutputStream(destination)){o.write(png);}
    return bmp;
  }
  public static String tap(int x,int y) throws Exception {
    if(x<0||y<0||x>10000||y>10000)throw new Exception("坐标超出范围");
    TouchGuard guard=touchGuard;
    if(guard!=null)guard.before(); // Remove input-intercepting status overlay.
    try {
      Process p=start("input tap "+x+" "+y);
      if(!p.waitFor(8,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("点击操作超时");}
      if(p.exitValue()!=0)throw new Exception("Root 点击失败，退出码："+p.exitValue());
      return "Root 点击命令已执行";
    } finally {
      if(guard!=null)guard.after(); // Restore stop/status UI even if action fails.
    }
  }
  public static void back() throws Exception {
    Process p=start("input keyevent 4");
    if(!p.waitFor(8,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("返回操作超时");}
    if(p.exitValue()!=0)throw new Exception("返回操作失败，退出码="+p.exitValue());
  }
  private static int scaled(int original, int actual, int reference) {
    return Math.max(0, Math.min(actual-1, Math.round(original * actual / (float)reference)));
  }
  private static boolean isPanelGray(int c) {
    int r=Color.red(c), g=Color.green(c), bl=Color.blue(c);
    int max=Math.max(r,Math.max(g,bl)), min=Math.min(r,Math.min(g,bl));
    int avg=(r+g+bl)/3;
    return max-min <= 17 && avg>=56 && avg<=110;
  }
  public static String classify(Bitmap b) {
    // Calibrated against two user-provided, 924x2048 domestic-server screenshots:
    // one with the march drawer open and one with it closed.
    // Only tests the characteristic gray backgrounds of four march rows.
    // No OCR or game-task decisions are inferred from this method.
    final int[] xRef={115,170,405};
    final int[] yRef={827,912,1002,1084};
    int hits=0, total=0;
    for(int y:yRef){
      for(int x:xRef){
        int px=scaled(x,b.getWidth(),924);
        int py=scaled(y,b.getHeight(),2048);
        if(isPanelGray(b.getPixel(px,py))) hits++;
        total++;
      }
    }
    boolean open=hits>=10;
    return (open?"疑似行军队列已展开":"未发现已展开的行军队列")
      +"\\n面板特征命中："+hits+"/"+total
      +"\\n识别模式：灰色行军列表区域采样"
      +"\\n说明：此结果尚非 OCR，不代表已准确识别队伍数量或空闲状态。";
  }
}
