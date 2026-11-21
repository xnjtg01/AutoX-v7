package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.util.Base64;
import java.util.Locale;

/** Domestic-server handshake shortcut, extracted from the user's 2026-11-21
 * screenshot (clean frame_011, no red annotation).
 *
 * Sample: a 40x36 crop at (379,839) in 540x960 reference coordinates.
 * Reference contains ONLY the hand/green ring, excluding notification badge,
 * chat text, nearby mail and bottom navigation.
 *
 * This detector searches only the known bottom-right help-button region and
 * never presses arbitrary "green" controls.
 */
public final class DomesticAllianceHelp {
  private DomesticAllianceHelp() {}
  private static final int COLUMNS=16, ROWS=14;
  private static final int REF_WIDTH=40, REF_HEIGHT=36;
  private static final String REF_RGB_BASE64=
    "iHRc8tu55dyj6Nyo8d6z/ee+3sWc6NCk7dmk7tej7s+g9N6t7+at6uCi89qX982Dmn9i7M2h9NWe9tSk+9Wu98yq1KJ/3aeB672J9sSR4q+A2Kx948CI+9GV8bZ6/L6L58Wg98yZ/Myb+tGj99u0/+XAoHpTyptx4qiA0Zdy2qeI+M2rlWU//7uS/aV93Zx26r2W78eM+M2a9Nak7ua48eq++OW6dFgpdF0xrpx05uG59PXN5d2vn35R3Z5156B21phz6Lx/8cKM9dCb8dKk/t2y9dSp+d2t/+O3+t2z9+Kz9N+w/Nuw/9awrGpKxHddYEAarmQ9waBq3bOB/86k/86m+NSk79ii9tuk+dij9tGd/9al8bOK4Jhy35px0aNxRjkWp2BEmlo0hEAbsnRN88SY99ek8tKf9NGZ9M2W/8uZ7q+D0odfpFQv1Ilh1ppmUls+cF9Br2tUuW5X/8WhuZNk2LqI/9Wq+saW8r2L0IthnVIryoBd4Z98h1IowZBlr7+lNF01VVk0X0Ah05J0xY9psIdbsHlS3Z92yYhe04RjxXRWs3RVsoZjkIJVOFMc9f/yccCTK5laG0kadEInkEsu5qqFmWY7eD0V1pJvq1o/zIBol3ZVT1QrR4ZNHqZc8/3y2vvoLZRcMJNaLFcpVkYikk4r0pJmzphsj1AtqnFTbEssGUIWNJlfC6JdEple/v/78/313froe8uoHJtkKo5aPVcyPjoXVUglNkMYGm4wI6FYB5dRIaZlWb2Lz/vi/vr7//b6//Dv/fr1z/zoiNO0L5lpHZBZFqFgD5xaHplgM5lneLiWy/Xd7//x+Pfz9vT3//r/9/fr6/3t5P/x8v/42/jkr+/Ni+G6kOC9sujQ2v/t7v/37vvy7/r08vb1";
  private static final byte[] reference=Base64.decode(REF_RGB_BASE64,Base64.NO_WRAP);
  private static final double THRESHOLD=.78;

  public static KingshotImageMatcher.Match find(Bitmap image) {
    int w=image.getWidth(),h=image.getHeight();
    if(w<300||h<500||reference.length!=COLUMNS*ROWS*3)
      return new KingshotImageMatcher.Match(false,0,0,0,"domestic/handshake");
    int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
    float sx=w/540f, sy=h/960f;
    int tw=Math.round(REF_WIDTH*sx),th=Math.round(REF_HEIGHT*sy);
    double best=-1;
    int bestX=0,bestY=0;
    // 540x960 reference bounds: hand popup is slightly above the alliance tab.
    int left=Math.max(0,Math.round(310*sx)),right=Math.min(w-tw,Math.round(462*sx));
    int top=Math.max(0,Math.round(765*sy)),bottom=Math.min(h-th,Math.round(900*sy));
    for(int y=top;y<=bottom;y+=3)for(int x=left;x<=right;x+=3) {
      double v=score(pixels,w,h,x,y,tw,th);
      if(v>best){best=v;bestX=x;bestY=y;}
    }
    int coarseX=bestX,coarseY=bestY;
    for(int y=Math.max(top,coarseY-3);y<=Math.min(bottom,coarseY+3);y++)
      for(int x=Math.max(left,coarseX-3);x<=Math.min(right,coarseX+3);x++){
        double v=score(pixels,w,h,x,y,tw,th);
        if(v>best){best=v;bestX=x;bestY=y;}
      }
    boolean pass=best>=THRESHOLD;
    int cx=bestX+tw/2,cy=bestY+th/2;
    return new KingshotImageMatcher.Match(pass,cx,cy,Math.max(0,best),
      "domestic/handshake");
  }
  private static double score(int[] pixels,int w,int h,int ox,int oy,int tw,int th){
    int n=COLUMNS*ROWS;
    double[] sumT=new double[3],sumI=new double[3];
    for(int iy=0;iy<ROWS;iy++)for(int ix=0;ix<COLUMNS;ix++){
      int k=iy*COLUMNS+ix;
      int px=ox+Math.min(tw-1,Math.round((ix+.5f)*tw/COLUMNS));
      int py=oy+Math.min(th-1,Math.round((iy+.5f)*th/ROWS));
      int c=pixels[py*w+px];
      int j=k*3;
      sumT[0]+=reference[j]&255;sumT[1]+=reference[j+1]&255;sumT[2]+=reference[j+2]&255;
      sumI[0]+=(c>>16)&255;sumI[1]+=(c>>8)&255;sumI[2]+=c&255;
    }
    for(int k=0;k<3;k++){sumT[k]/=n;sumI[k]/=n;}
    double dot=0,vT=0,vI=0;
    for(int iy=0;iy<ROWS;iy++)for(int ix=0;ix<COLUMNS;ix++){
      int k=iy*COLUMNS+ix,j=k*3;
      int px=ox+Math.min(tw-1,Math.round((ix+.5f)*tw/COLUMNS));
      int py=oy+Math.min(th-1,Math.round((iy+.5f)*th/ROWS));
      int c=pixels[py*w+px];
      double[] v={((c>>16)&255)-sumI[0],((c>>8)&255)-sumI[1],
        (c&255)-sumI[2]};
      for(int ch=0;ch<3;ch++){
        double t=(reference[j+ch]&255)-sumT[ch];
        dot+=t*v[ch];vT+=t*t;vI+=v[ch]*v[ch];
      }
    }
    return vT<1||vI<1?-1:dot/Math.sqrt(vT*vI);
  }
}
