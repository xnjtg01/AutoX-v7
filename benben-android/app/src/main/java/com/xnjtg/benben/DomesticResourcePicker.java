package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.Locale;

/**
 * Domestic-server wilderness resource picker.
 *
 * Grounded in the original user's APK script.uip ("面包/木材/石材/铁矿")
 * and 2026-11-21 domestic wilderness screenshot: horizontal tab strip
 * across y=890..1000 of a 720x1280 screen, with an aqua "搜索" button.
 *
 * Unlike Kingshot's static farming/stone.png image, this implementation
 * uses localized OCR, bounded horizontal swipes, and no guessing clicks.
 */
public final class DomesticResourcePicker {
  public static final String[] RESOURCES={"石材","木材","面包","铁矿"};
  public interface IO {
    Bitmap screenshot()throws Exception;
    void save(Bitmap image,String label)throws Exception;
    void event(String level,String label,String detail);
    boolean stopped();
  }
  private final ChineseOcr ocr;
  private final IO io;
  public DomesticResourcePicker(ChineseOcr ocr,IO io){this.ocr=ocr;this.io=io;}

  private static int at(Bitmap b,float x,float y){
    return b.getPixel(Math.min(b.getWidth()-1,Math.round(x*(b.getWidth()-1))),
                      Math.min(b.getHeight()-1,Math.round(y*(b.getHeight()-1))));
  }
  private static boolean cyan(int p){
    int r=Color.red(p),g=Color.green(p),bl=Color.blue(p);
    return g>=147&&bl>=149&&g>r+45&&bl>r+45;
  }
  public static boolean searchPanelVisible(Bitmap b){
    // This is the large turquoise SEARCH button inside a beige bottom drawer.
    // It is not the small yellow/orange world search loupe icon.
    int buttonHits=0;
    for(float x:new float[]{.34f,.42f,.50f,.58f,.67f})
      if(cyan(at(b,x,.95f)))buttonHits++;
    int p=at(b,.50f,.835f);
    boolean beige=Color.red(p)>=190&&Color.green(p)>=170&&
       Color.blue(p)>=140&&Color.red(p)>Color.blue(p)+15;
    return buttonHits>=3&&beige;
  }
  private static final class Target {
    final int x,y;
    final String observed;
    Target(int x,int y,String observed){this.x=x;this.y=y;this.observed=observed;}
  }
  private Target locate(Bitmap b,String resource)throws Exception {
    ChineseOcr.Result found=ocr.read(b);
    for(ChineseOcr.Item line:found.lines){
      if(line.box==null)continue;
      String label=ChineseOcr.compact(line.text);
      String search=resource;
      if("面包".equals(resource)&&label.contains("粮食")&&!label.contains("面包"))
        search="粮食";
      int idx=label.indexOf(search);
      if(idx<0)continue;
      int top=line.box.top,bottom=line.box.bottom;
      int y=(top+bottom)/2;
      // Category text appears below the icons near the top of the beige drawer.
      if(y<b.getHeight()*.70f||y>b.getHeight()*.84f)continue;
      int x=line.box.centerX();
      if(label.length()>search.length()){
        float fraction=(idx+search.length()*.5f)/(float)label.length();
        x=Math.round(line.box.left+line.box.width()*fraction);
      }
      // Avoid clicking clipped icons at the edges.
      if(x<b.getWidth()*.075f||x>b.getWidth()*.95f)continue;
      return new Target(x,y,label);
    }
    return null;
  }
  private int sx(Bitmap b,int x){return Math.round(x*b.getWidth()/720f);}
  private int sy(Bitmap b,int y){return Math.round(y*b.getHeight()/1280f);}
  public boolean selectAndSearch(String resource)throws Exception{
    if(resource==null||resource.trim().isEmpty())resource="石材";
    boolean known=false;
    for(String type:RESOURCES)if(type.equals(resource))known=true;
    if(!known)throw new IllegalArgumentException("无效资源类型 "+resource);
    io.event("START","资源采集","正在横向检索国内服「"+resource+"」类别，最多来回滑动 6 次");
    boolean startedLeft=false;
    for(int i=0;i<7&&!io.stopped();i++){
      Bitmap image=io.screenshot();
      try{
        if(!searchPanelVisible(image)){
          io.save(image,"采集-搜索面板不符合预期");
          io.event("SKIP","资源采集","搜索面板不在前台，没有点击类别");
          return false;
        }
        Target target;
        try{target=locate(image,resource);}
        catch(Exception e){
          io.event("WARN","资源采集","类别中文 OCR 失败："+e.getMessage());
          target=null;
        }
        if(target!=null){
          int clickX=target.x,clickY=target.y;
          io.event("INFO","资源采集","看到 "+target.observed+
            "，已定位「"+resource+"」按钮中心：("+clickX+","+clickY+")");
          io.save(image,"采集-找到"+resource+"类别");
          RootBridge.tap(clickX,clickY);
          Thread.sleep(550);
          Bitmap chosen=io.screenshot();
          try{
            io.save(chosen,"采集-"+resource+"选中后");
            if(!searchPanelVisible(chosen)){
              io.event("FAIL","资源采集","点击类别后底部搜索面板消失，停止出征");
              return false;
            }
            // Search button is centered near x=360/y=1217 in the domestic UI.
            ChineseOcr.Result result=ocr.read(chosen);
            int searchX=-1,searchY=-1;
            for(ChineseOcr.Item row:result.lines){
              if(row.box==null)continue;
              String text=ChineseOcr.compact(row.text);
              int x=row.box.centerX(),y=row.box.centerY();
              if((text.equals("搜索")||text.equals("搜素"))&&
                  x>=sx(chosen,180)&&x<=sx(chosen,560)&&
                  y>=sy(chosen,1150)&&y<=sy(chosen,1265)){
                searchX=x;searchY=y;break;
              }
            }
            if(searchX<0){
              // The button color is independently checked above; use its fixed
              // center only when the Chinese label could not be segmented.
              searchX=sx(chosen,360);searchY=sy(chosen,1218);
              io.event("INFO","资源采集","未分割出搜索文字，采用已确认青色按钮中心");
            }
            if(io.stopped())return false;
            RootBridge.tap(searchX,searchY);
            io.event("INFO","资源采集","已选 "+resource+"，执行单次搜索："+searchX+","+searchY);
            Thread.sleep(1400);
            Bitmap after=io.screenshot();
            try{io.save(after,"采集-"+resource+"搜索结果");}
            finally{after.recycle();}
            return true;
          }finally{chosen.recycle();}
        }
        if(i==6)break;
        // Each side of the carousel is explored at most 3 times;
        // move left first to reveal iron, then back right to find bread etc.
        if(i==3)startedLeft=true;
        boolean left=!startedLeft;
        int x1=sx(image,left?580:150),x2=sx(image,left?165:575);
        int y=sy(image,929);
        io.event("INFO","资源采集","本页未找到 "+resource+"，横向滑动 "+(left?"向左":"向右")+
          " 第"+(i+1)+"/6次");
        RootBridge.swipe(x1,y,x2,y,430);
      }finally{image.recycle();}
      Thread.sleep(620);
    }
    io.event("SKIP","资源采集","左右滑动后仍未识别到 "+resource+"，不执行搜索或派兵");
    return false;
  }
}
