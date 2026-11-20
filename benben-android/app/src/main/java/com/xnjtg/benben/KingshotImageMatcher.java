package com.xnjtg.benben;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Template matching against bundled Kingshot source templates.
 * Portable Android implementation: does not need Windows ADB or a native OpenCV binary.
 * This is a conservative read-only detector; callers decide whether to click.
 */
public final class KingshotImageMatcher {
  private final AssetManager assets;
  public KingshotImageMatcher(AssetManager assets) { this.assets=assets; }
  public static final class Match {
    public final boolean found;
    public final int x, y;
    public final double confidence;
    public final String template;
    Match(boolean found,int x,int y,double confidence,String template){
      this.found=found;this.x=x;this.y=y;this.confidence=confidence;this.template=template;
    }
    @Override public String toString(){
      return template+" found="+found+" x="+x+" y="+y+" confidence="+
        String.format(java.util.Locale.US,"%.3f",confidence);
    }
  }
  private static class Sample {
    int x,y,intensity;
    Sample(int x,int y,int intensity){this.x=x;this.y=y;this.intensity=intensity;}
  }
  private static int intensity(int argb){
    return (android.graphics.Color.red(argb)*54+
      android.graphics.Color.green(argb)*183+
      android.graphics.Color.blue(argb)*19)>>8;
  }
  private static double score(int[] image,int width,int x,int y,
                              int templateW,int templateH,
                              ArrayList<Sample> samples,double meanT,double varT,
                              float scale) {
    int n=samples.size();
    double sum=0,sumSq=0,cross=0;
    for(Sample sample:samples){
      int sx=x+Math.round(sample.x*scale);
      int sy=y+Math.round(sample.y*scale);
      if(sx<0||sy<0||sx>=width||sy>=image.length/width)return -1;
      int z=intensity(image[sy*width+sx]);
      sum+=z;sumSq+=z*z;
      cross+=(z)*(sample.intensity-meanT);
    }
    double var=sumSq-sum*sum/n;
    if(var<10 || varT<10)return -1;
    return cross/Math.sqrt(var*varT);
  }
  public Match find(Bitmap screenshot,String templatePath,double threshold)throws Exception {
    return findInReferenceArea(screenshot,templatePath,threshold,0,0,720,1280);
  }
  public Match findInReferenceArea(Bitmap screenshot,String templatePath,double threshold,
                                   int rx,int ry,int rw,int rh)throws Exception {
    if(rw<=0||rh<=0)throw new IllegalArgumentException("Empty search region");
    final String asset="kingshot/"+templatePath;
    Bitmap template;
    try(InputStream in=assets.open(asset)){template=BitmapFactory.decodeStream(in);}
    if(template==null)throw new IllegalArgumentException("Cannot decode "+asset);
    try{
      int width=screenshot.getWidth(),height=screenshot.getHeight();
      int[] image=new int[width*height];
      screenshot.getPixels(image,0,width,0,0,width,height);
      int tw=template.getWidth(),th=template.getHeight();
      int[] pixels=new int[tw*th];
      template.getPixels(pixels,0,tw,0,0,tw,th);
      ArrayList<Sample> samples=new ArrayList<>();
      final int stride=Math.max(1,Math.min(tw,th)/12);
      double sumT=0,sumSqT=0;
      for(int y=stride/2;y<th;y+=stride)for(int x=stride/2;x<tw;x+=stride){
        int px=pixels[y*tw+x];
        if(android.graphics.Color.alpha(px)<180)continue;
        int gray=intensity(px);
        samples.add(new Sample(x,y,gray));
        sumT+=gray;sumSqT+=gray*gray;
      }
      if(samples.size()<25)return new Match(false,0,0,0,templatePath);
      double meanT=sumT/samples.size();
      double varT=sumSqT-sumT*sumT/samples.size();
      double best=-1;int bestX=0,bestY=0;float bestScale=1f;
      float ratio=Math.min(width/720f,height/1280f);
      int left=Math.max(0,Math.round(rx*width/720f));
      int top=Math.max(0,Math.round(ry*height/1280f));
      int right=Math.min(width,Math.round((rx+rw)*width/720f));
      int bottom=Math.min(height,Math.round((ry+rh)*height/1280f));
      for(float factor:new float[]{.85f,1f,1.15f}){
        float scale=ratio*factor;
        int sw=Math.round(tw*scale),sh=Math.round(th*scale);
        if(sw<12||sh<12||sw>=width||sh>=height)continue;
        for(int yy=top;yy<=bottom-sh;yy+=4){
          for(int xx=left;xx<=right-sw;xx+=4){
            double conf=score(image,width,xx,yy,tw,th,samples,meanT,varT,scale);
            if(conf>best){best=conf;bestX=xx;bestY=yy;bestScale=scale;}
          }
        }
      }
      if(best>.55) {
        int bx=bestX,by=bestY;
        for(int dy=-5;dy<=5;dy++)for(int dx=-5;dx<=5;dx++){
          if(bx+dx<left||by+dy<top||bx+dx+Math.round(tw*bestScale)>right||by+dy+Math.round(th*bestScale)>bottom)continue;
          double conf=score(image,width,bx+dx,by+dy,tw,th,samples,meanT,varT,bestScale);
          if(conf>best){best=conf;bestX=bx+dx;bestY=by+dy;}
        }
      }
      return new Match(best>=threshold,
        Math.round(bestX+tw*bestScale/2),
        Math.round(bestY+th*bestScale/2),
        best,templatePath);
    } finally {template.recycle();}
  }
}
