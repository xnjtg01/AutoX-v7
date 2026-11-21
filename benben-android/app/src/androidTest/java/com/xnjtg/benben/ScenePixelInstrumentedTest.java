package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Runs on a real headless Android emulator in GitHub Actions.
 *
 * Fixtures are SYNTHETIC and contain no user game account screenshots.
 * These checks exercise Android's own Bitmap, color and matching code rather
 * than only the Java-only JUnit tests used in the APK build.
 */
@RunWith(AndroidJUnit4.class)
public final class ScenePixelInstrumentedTest {
  private static Bitmap bitmap(int color) {
    Bitmap b=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);
    b.eraseColor(color);
    return b;
  }
  private static void dot(Bitmap b,float x,float y,int color) {
    int px=Math.round((b.getWidth()-1)*x);
    int py=Math.round((b.getHeight()-1)*y);
    b.setPixel(px,py,color);
  }
  @Test public void cloudSceneMustWaitNotPressBack() {
    Bitmap b=bitmap(Color.rgb(224,232,237));
    DomesticScreenNavigation n=new DomesticScreenNavigation(null,null,null);
    try{
      assertTrue("White cloud frame must be seen as transition",n.mapTransition(b));
      assertEquals(DomesticScreenNavigation.Page.TRANSITION,n.detect(b));
    }finally{b.recycle();}
  }
  @Test public void normalDarkWorldFrameIsNotCloud() {
    Bitmap b=bitmap(Color.rgb(31,79,69));
    try{
      assertFalse(new DomesticScreenNavigation(null,null,null).mapTransition(b));
    }finally{b.recycle();}
  }
  @Test public void domesticResourceDrawerNeedsCyanSearchButton() {
    Bitmap b=bitmap(Color.rgb(29,89,67));
    Canvas canvas=new Canvas(b);
    Paint paint=new Paint();
    paint.setColor(Color.rgb(222,201,171));
    canvas.drawRect(0,1000,720,1280,paint);
    paint.setColor(Color.rgb(38,201,209));
    canvas.drawRect(220,1170,500,1250,paint);
    try{
      assertTrue("Domestic search drawer present",DomesticResourcePicker.searchPanelVisible(b));
    }finally{b.recycle();}
  }
  @Test public void panelWithoutSearchButtonCannotBeResourceSearch() {
    Bitmap b=bitmap(Color.rgb(33,85,57));
    Canvas canvas=new Canvas(b);
    Paint paint=new Paint();
    paint.setColor(Color.rgb(223,199,170));
    canvas.drawRect(0,1000,720,1280,paint);
    try{assertFalse(DomesticResourcePicker.searchPanelVisible(b));}
    finally{b.recycle();}
  }
  @Test public void growthPanelSyntheticColorHeaderAndTabs() {
    Bitmap b=bitmap(Color.BLACK);
    dot(b,.5f,.157f,Color.rgb(47,170,234));
    dot(b,.25f,.875f,Color.rgb(235,221,203));
    dot(b,.68f,.875f,Color.rgb(145,110,75));
    try{
      assertTrue(new DomesticScreenNavigation(null,null,null).missionPanel(b));
    }finally{b.recycle();}
  }
  @Test public void randomDarkSceneHasNoDynamicRallyOrHelpShortcut(){
    Bitmap b=bitmap(Color.rgb(19,26,33));
    try{
      assertFalse(DomesticRallyShortcut.find(b).found);
      assertFalse(DomesticAllianceHelp.find(b).found);
    }finally{b.recycle();}
  }
}