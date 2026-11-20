package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;

/**
 * Independent, conservative recognizers calibrated from user's own game screenshots.
 * No original third-party bytecode, encrypted assets, or commercial licensing code.
 */
public final class VisualEngine {
  private VisualEngine() {}
  public static final class Observation {
    public final boolean welcome;
    public final boolean city;
    public final double helpError;
    public final boolean quickHelp;
    public final int width,height;
    Observation(boolean w,boolean c,double e,int width,int height) {
      this.welcome=w;this.city=c;this.helpError=e;this.quickHelp=c&&e<38;this.width=width;this.height=height;
    }
    @Override public String toString() {
      return "欢迎回来弹窗="+welcome+"；城镇底栏="+city+"；联盟互助图标误差="+
        String.format(java.util.Locale.US,"%.1f",helpError)+
        "；可安全识别互助="+quickHelp+"；分辨率="+width+"x"+height;
    }
  }
  private static final byte[] HELP_REF=Base64.decode("YXZv3+nm9/783/btltu+YcOVX8SUkNa31Ni50pQz4LN01JkuhpOT8fr43eXaerWRH6BiFZ5cD6BaF6FgdrSK17x+1qhQ0Z5ClZ+j6+7p1ceu19auqL6RobWJobiMlbOFsbmR1smv3NO4xb21lp6g3tbI5sOZ9teo4MKY4beK4LmK876K6K+D1MayqKuolZyczbij8cGP+NSm6s6ky6p9276W4MaZ1p1u5Z5s07Scra2vlJuYt6aOyY9j4raJ8dKl99Wn99eo8cOX1JZrtH1Tu66Wqa6zl52dyM26h2RHuXlZ06V478GS5auBzIZhuHhUinxXuNS+rq61l52d5u/lb6B6Y2Q8t3lXvoVduHRRoXBOX3tKYK2A5fDnqq6tkJaW9fbxy+rWUql4TG9AfW1FY3NEN4lQSbN9xujT+Pbzp6ysYmtq5uXl+Pb03PHln9G2b7WRabySlNW12u/i9/fy6e3sfYCNLjo3eoOCwsjJzc/S0djX7vHv8PTw1drby8/WwszPjpSbS05nfX91enxzf4F3fYB3gYWBxsrKztTUgYaGe3V6gnFylH9+qqKc",Base64.NO_WRAP);
  private static int px(Bitmap b,float nx,float ny) {
    int x=Math.min(b.getWidth()-1,Math.max(0,Math.round(nx*(b.getWidth()-1))));
    int y=Math.min(b.getHeight()-1,Math.max(0,Math.round(ny*(b.getHeight()-1))));
    return b.getPixel(x,y);
  }
  private static boolean beige(int c) {
    int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
    return r>=170&&r<=250&&g>=145&&g<=242&&b>=115&&b<=230&&r>=g&&g>=b;
  }
  private static boolean vividGreen(int c) {
    int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
    return g>=132 && g>=r+33 && g>=b+31 && r<=170 && b<=165;
  }
  public static boolean welcome(Bitmap bitmap) {
    float[][] panel={{.15f,.34f},{.85f,.34f},{.18f,.63f},{.8f,.73f},{.05f,.40f}};
    int beigeHits=0;
    for(float[] point:panel)if(beige(px(bitmap,point[0],point[1])))beigeHits++;
    int green=0;
    for(float x:new float[]{.35f,.43f,.57f,.64f})
      for(float y:new float[]{.787f,.815f})
        if(vividGreen(px(bitmap,x,y)))green++;
    // The confirm button exists only inside a beige dialog; combined gate prevents
    // clicking similarly colored buttons anywhere else.
    return beigeHits>=4 && green>=7 && beige(px(bitmap,.25f,.80f));
  }
  public static boolean city(Bitmap b) {
    int hit=0;
    for(float x:new float[]{.02f,.19f,.36f,.53f,.70f,.97f}){
      int c=px(b,x,.984f);
      int r=Color.red(c),g=Color.green(c),blue=Color.blue(c);
      if(r>=130&&r<=230&&g>=117&&g<=214&&blue>=87&&blue<=183&&r>g&&g>blue)hit++;
    }
    return hit>=5;
  }
  public static double helpError(Bitmap b) {
    // Matching a 12x12 color template extracted from the user-supplied domestic
    // city screenshot, anchored near the quick alliance-help speech bubble.
    double min=1000;
    for(int dy=-9;dy<=9;dy+=3)for(int dx=-9;dx<=9;dx+=3) {
      long err=0; int index=0;
      for(int yy=0;yy<12;yy++)for(int xx=0;xx<12;xx++){
        float rx=(370+dx+(xx+.5f)*63/12f)/540f;
        float ry=(825+dy+(yy+.5f)*60/12f)/960f;
        int c=px(b,rx,ry);
        int rr=HELP_REF[index++]&255,gg=HELP_REF[index++]&255,bb=HELP_REF[index++]&255;
        err+=Math.abs(rr-Color.red(c))+Math.abs(gg-Color.green(c))+Math.abs(bb-Color.blue(c));
      }
      double score=err/(144.0*3);
      if(score<min)min=score;
    }
    return min;
  }
  public static Observation inspect(Bitmap b) {
    return new Observation(welcome(b),city(b),helpError(b),b.getWidth(),b.getHeight());
  }
  public static int helpX(Bitmap b){return Math.round(b.getWidth()*(400f/540f));}
  public static int helpY(Bitmap b){return Math.round(b.getHeight()*(854f/960f));}
  public static int confirmX(Bitmap b){return Math.round(b.getWidth()*.50f);}
  public static int confirmY(Bitmap b){return Math.round(b.getHeight()*.802f);}
}