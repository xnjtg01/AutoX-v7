package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import java.util.Locale;

/**
 * Detects the domestic-server red "beast rally" shortcut.
 * Its position is dynamic and the timer/number badge vary; only the inner
 * beast medallion is matched. User-provided (unannotated) diagnostics
 * frame_024..030 from 2026-11-21 are the reference.
 */
public final class DomesticRallyShortcut {
  private DomesticRallyShortcut(){}
  private static final int COLS=16, ROWS=14;
  private static final int REF_WIDTH=38, REF_HEIGHT=36; // crop from 540x960
  private static final byte[] RGB=Base64.decode(
    "PikkwHl12ZqV//fn5rCmlVpMy4l75aeas3Vmu3RU1IlhzIVb1YRpv3RfqGtZ2piMpXB6r0hBsEhFwnN29//2+enc/9zVvWxpwmlh1XtYxGc+wmZPyHhTy3tWxHNWnWJc3JKhxVJAukY5v0xR8+Da//btx5aP/+De/+DdwYx+/8e2+8zC672txpmG/9nI6sK6uGduzkw8wFY+o1tNw21uoFBJi09E//z09P//9fn///3/8f//+P//9P//8v////vuv1tZ12lqwFNM3rivtmRZxmherFVL/+ff//37//T6//j7/v/9+Pz7//r8//r61bGlxFhY2Whsu1RP7tPMtlJQ0mxoqmFa//j0vZqh/+zs///0/f/8//X9/9Pet46S/+/dz1ha3mlxsUtH9+Ldu1pH4GZhnU1G//z3/+fz4srK8v/x9f79+NrkyqKr9NvX897NzVta0lleu05L7s3Is2JF5m1irVBI///0//r///b7+f/7/vn99evs9Ojo9e7m/+vfxF5ZzVBO0k9H046Hp2pnsWRcoFdI/c3BzJya/+npy6yn0JiX18Cy9djK3a+iz4+F4IN70VJM3lBGpE1EzZOp0qSk5LOirldOwWNj+r62o25gsFZO5q+aw4BtwF9WtlhO0a6qvlJGvldOr1ZS/+Dn/Pfx//3usVta6GtvuGBS3qeSyIN+5YuAzWFU8IF20I2HbIiTwmdV2YN0//Pm9uXepn96/8/KqVlOtlxR7nNu6WNi3Wpl2W5o23Ju4Hl6rmFnTZKivZKLwIR6/+PZq2xjrFRQtVJM/821zaaJp2VXwGNexF5ZrFxVqV5Z1oaH3oiLWLzLRX2MpJ2lwnN41ktE1k1Dx0c6rVJAyYZ2/czI/+jl/+vf/+bY6sm6yI+Er09B",
    Base64.NO_WRAP);
  private static final int N=COLS*ROWS;
  private static final double[] T_MEAN=new double[3];
  private static final double TVAR;
  static {
    for(int i=0;i<N;i++){
      T_MEAN[0]+=RGB[3*i]&255;
      T_MEAN[1]+=RGB[3*i+1]&255;
      T_MEAN[2]+=RGB[3*i+2]&255;
    }
    for(int j=0;j<3;j++)T_MEAN[j]/=N;
    double tvar=0;
    for(int i=0;i<N;i++)for(int j=0;j<3;j++){
      double t=(RGB[3*i+j]&255)-T_MEAN[j];
      tvar+=t*t;
    }
    TVAR=tvar;
  }
  public static KingshotImageMatcher.Match find(Bitmap image) {
    if(RGB.length!=N*3||image==null)
      return new KingshotImageMatcher.Match(false,0,0,0,"domestic/red-beast-rally");
    int w=image.getWidth(),h=image.getHeight();
    int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
    double best=-1;
    int bestX=0,bestY=0,bestW=0,bestH=0;
    for(float size:new float[]{.93f,1f,1.07f}){
      int tw=Math.round(REF_WIDTH*w/540f*size);
      int th=Math.round(REF_HEIGHT*h/960f*size);
      if(tw<16||th<15)continue;
      // Search a large area; shortcut may move due to activity overlays.
      int left=Math.round(w*.08f),right=Math.min(w-tw,Math.round(w*.96f));
      int top=Math.round(h*.08f),bottom=Math.min(h-th,Math.round(h*.78f));
      for(int y=top;y<=bottom;y+=7)
        for(int x=left;x<=right;x+=7){
          double v=score(pixels,w,x,y,tw,th);
          if(v>best){best=v;bestX=x;bestY=y;bestW=tw;bestH=th;}
        }
    }
    if(best>0.61) {
      int ax=bestX,ay=bestY;
      for(int y=Math.max(0,ay-8);y<=Math.min(h-bestH,ay+8);y++)
        for(int x=Math.max(0,ax-8);x<=Math.min(w-bestW,ax+8);x++){
          double v=score(pixels,w,x,y,bestW,bestH);
          if(v>best){best=v;bestX=x;bestY=y;}
        }
    }
    // The icon itself is a red ring with a white beast, NOT the timer or red
    // notification number. A deliberately high threshold avoids other events.
    boolean found=best>=.80;
    return new KingshotImageMatcher.Match(found,bestX+bestW/2,bestY+bestH/2,
      Math.max(0,best),"domestic/red-beast-rally");
  }
  private static double score(int[] pix,int width,int x,int y,int tw,int th){
    if(TVAR<10)return -1;
    double rsum=0,gsum=0,bsum=0;
    int[] offsets=new int[N];
    int k=0;
    int redCount=0;
    for(int ry=0;ry<ROWS;ry++)for(int rx=0;rx<COLS;rx++){
      int px=x+Math.min(tw-1,Math.round((rx+.5f)*tw/COLS));
      int py=y+Math.min(th-1,Math.round((ry+.5f)*th/ROWS));
      int c=pix[py*width+px];offsets[k++]=c;
      int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
      rsum+=r;gsum+=g;bsum+=b;
      if(r>g+29&&r>b+29&&r>125)redCount++;
    }
    if(redCount<N*.40)return -1;
    double mr=rsum/N,mg=gsum/N,mb=bsum/N;
    double dot=0,var=0;
    for(int i=0;i<N;i++){
      int c=offsets[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;
      double dr=r-mr,dg=g-mg,db=b-mb;
      int n=i*3;
      dot+=dr*((RGB[n]&255)-T_MEAN[0]);
      dot+=dg*((RGB[n+1]&255)-T_MEAN[1]);
      dot+=db*((RGB[n+2]&255)-T_MEAN[2]);
      var+=dr*dr+dg*dg+db*db;
    }
    return var<10?-1:dot/Math.sqrt(var*TVAR);
  }
}