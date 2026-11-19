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
    Process p=start("input tap "+x+" "+y);
    if(!p.waitFor(8,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("点击操作超时");}
    if(p.exitValue()!=0)throw new Exception("Root 点击失败，退出码："+p.exitValue());
    return "Root 点击命令已执行";
  }
  public static String classify(Bitmap b) {
    // 基于用户提供的 924x2048 截图作初步颜色特征测试，并非完整图像识别。
    int[][] probe={{100,800},{250,800},{450,1000},{100,1100},{30,900}};
    int hit=0;
    for(int[] p:probe){
      int x=Math.min(b.getWidth()-1,Math.max(0,Math.round(p[0]*b.getWidth()/924f)));
      int y=Math.min(b.getHeight()-1,Math.max(0,Math.round(p[1]*b.getHeight()/2048f)));
      int c=b.getPixel(x,y);
      if((Color.red(c)+Color.green(c)+Color.blue(c))/3<110)hit++;
    }
    return "画面特征命中："+hit+"/5；"+(hit>=4?"疑似行军面板展开":"未确认行军面板")+"（仅粗略推断，需真实截图验证）";
  }
}
