package com.xnjtg.benben;

import org.junit.Test;
import static org.junit.Assert.*;

/** Prevent the V2.4 regression: do not use English farming/deploy-button.png
 * on the Chinese troop setup page. Independent checks must agree.
 */
public final class DomesticMarchDeployTest {
  @Test public void screenshotTitleRecognizesDomesticResource(){
    assertTrue(DomesticMarchDeploy.resourceMatches("石材 33,830/111,945 出征","石材"));
    assertTrue(DomesticMarchDeploy.resourceMatches("等级8 空闲的采石场","石材"));
    assertTrue(DomesticMarchDeploy.resourceMatches("伐木场","木材"));
    assertFalse(DomesticMarchDeploy.resourceMatches("石材","铁矿"));
  }
  @Test public void requiresPositiveTroopLoadBeforeDispatch(){
    assertTrue(DomesticMarchDeploy.hasPositiveTroopCount("33,830/111,945"));
    assertTrue(DomesticMarchDeploy.hasPositiveTroopCount("33830 / 111945"));
    assertFalse(DomesticMarchDeploy.hasPositiveTroopCount("0 / 111945"));
    assertFalse(DomesticMarchDeploy.hasPositiveTroopCount("111946/111945"));
    assertFalse(DomesticMarchDeploy.hasPositiveTroopCount("训练中 11:00:44"));
  }
  @Test public void dispatchButtonIsBottomRightNotLegacyEnglishTemplate(){
    assertTrue(DomesticMarchDeploy.validButtonRegion(720,1280,547,1208));
    assertFalse(DomesticMarchDeploy.validButtonRegion(720,1280,190,193));
    assertFalse(DomesticMarchDeploy.validButtonRegion(720,1280,550,628));
    int[] p=DomesticMarchDeploy.fallbackButtonCenter(720,1280);
    assertArrayEquals(new int[]{547,1210},p);
  }
  @Test public void scaled540ReferenceIsSameRelativeButton(){
    int[] p=DomesticMarchDeploy.fallbackButtonCenter(540,960);
    assertArrayEquals(new int[]{410,907},p);
    assertTrue(DomesticMarchDeploy.validButtonRegion(540,960,p[0],p[1]));
  }
}
