package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Domestic Kingshot troop deployment screen after pressing 采集.
 *
 * Based on the 2026-11-21 V2.4 diagnostic frame_028:
 * - title "石材" at top; troop rows 王牌步兵/骑兵/弓兵;
 * - positive troop-count number (e.g. 33,830/111,945);
 * - bottom cyan "出征" button around reference (550,1208) at 720x1280.
 *
 * All conditions must be met before a single dispatch tap. This replaces the
 * English farming/deploy-button.png which scored 0.281 in V2.4.
 */
public final class DomesticMarchDeploy {
  public static final class Target {
    public final boolean confirmed;
    public final int x,y;
    public final String note;
    Target(boolean confirmed,int x,int y,String note) {
      this.confirmed=confirmed;this.x=x;this.y=y;this.note=note;
    }
    @Override public String toString(){return "confirmed="+confirmed+
      " ("+x+","+y+") "+note;}
  }
  private DomesticMarchDeploy() {}
  public static boolean resourceMatches(String text,String resource){
    if(text==null||resource==null)return false;
    if(text.contains(resource))return true;
    if(resource.equals("石材"))return text.contains("采石场");
    if(resource.equals("木材"))return text.contains("伐木场");
    if(resource.equals("铁矿"))return text.contains("铁矿场");
    if(resource.equals("面包"))return text.contains("农田")||text.contains("麦田");
    return false;
  }
  public static boolean hasPositiveTroopCount(String text){
    if(text==null)return false;
    // E.g. "33,830/111,945" or "33830/111945" in domestic deployment.
    java.util.regex.Matcher m=java.util.regex.Pattern.compile(
      "([0-9][0-9,]{0,9})\\s*/\\s*([0-9][0-9,]{0,9})").matcher(text);
    while(m.find()){
      try{
        long selected=Long.parseLong(m.group(1).replace(",",""));
        long capacity=Long.parseLong(m.group(2).replace(",",""));
        if(selected>0&&capacity>0&&selected<=capacity)return true;
      }catch(NumberFormatException ignore){}
    }
    return false;
  }
  public static boolean validButtonRegion(int width,int height,int x,int y){
    return width>0&&height>0 && x>=width*.50f&&x<=width*.97f &&
      y>=height*.885f&&y<=height*.975f;
  }
  public static int[] fallbackButtonCenter(int width,int height){
    return new int[]{Math.round(width*.76f),Math.round(height*.945f)};
  }
  private static boolean cyan(Bitmap b){
    int x=Math.round(b.getWidth()*.58f),y=Math.round(b.getHeight()*.943f);
    int c=b.getPixel(Math.max(0,Math.min(b.getWidth()-1,x)),
      Math.max(0,Math.min(b.getHeight()-1,y)));
    int r=Color.red(c),g=Color.green(c),blue=Color.blue(c);
    return g>=145&&blue>=150&&g>r+45&&blue>r+45;
  }
  public static Target inspect(Bitmap image,ChineseOcr ocr,String resource)throws Exception {
    if(image==null)throw new IllegalArgumentException("No image");
    ChineseOcr.Result recognized=ocr.read(image);
    String text=ChineseOcr.compact(recognized.allText);
    int w=image.getWidth(),h=image.getHeight();
    String title=ChineseOcr.compact(recognized.inArea(0,0,(int)(w*.72f),(int)(h*.12f)));
    if(!resourceMatches(title,resource))
      return new Target(false,0,0,"出征页标题不是 "+resource+"，识别为："+title);
    boolean troopLabel=text.contains("步兵")||text.contains("骑兵")||text.contains("弓兵");
    String topTroops=ChineseOcr.compact(recognized.inArea(0,(int)(h*.105f),
      (int)(w*.70f),(int)(h*.11f)));
    if(!troopLabel||!hasPositiveTroopCount(topTroops))
      return new Target(false,0,0,"没有确认顶部出征人数为正数："+topTroops);
    if(!cyan(image))
      return new Target(false,0,0,"底部出征按钮颜色不符，不能猜测位置");
    for(ChineseOcr.Item line:recognized.lines){
      if(line.box==null)continue;
      String value=ChineseOcr.compact(line.text);
      int x=line.box.centerX(),y=line.box.centerY();
      if(value.equals("出征")&&validButtonRegion(image.getWidth(),image.getHeight(),x,y))
        return new Target(true,x,y,"资源、兵力和出征文字/按钮均已确认");
    }
    // If OCR missed the rendered white text, the cyan shape and positive
    // troop count still independently confirm this specific screen.
    int[] p=fallbackButtonCenter(image.getWidth(),image.getHeight());
    return new Target(true,p[0],p[1],"已确认资源/正兵力/青色按钮，出征文字未分段");
  }
}
