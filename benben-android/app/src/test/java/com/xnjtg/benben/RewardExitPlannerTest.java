package com.xnjtg.benben;

import org.junit.Test;
import static org.junit.Assert.*;

/** Offline regression tests: every APK build must pass these before upload.
 * Synthetic geometry only; no user gameplay screenshots enter public GitHub.
 */
public final class RewardExitPlannerTest {
  @Test public void reference720MustTargetBottomPromptNotRewardGrid(){
    int[] result=RewardExitPlanner.exitTap(720,1280,0,-1,-1);
    assertEquals(367,result[0]);
    assertEquals(1178,result[1]);
    assertTrue(RewardExitPlanner.promptLocation(720,1280,result[0],result[1]));
    assertFalse("V2.3 wrong tap (360,960) must never count as prompt",
      RewardExitPlanner.promptLocation(720,1280,360,960));
  }
  @Test public void ocrCoordinatesTakePriorityWithinBottomPrompt(){
    int[] r=RewardExitPlanner.exitTap(720,1280,0,350,1187);
    assertArrayEquals(new int[]{350,1187},r);
  }
  @Test public void OCRCoordinatesInRewardGridAreRejected(){
    int[] r=RewardExitPlanner.exitTap(720,1280,0,360,960);
    assertEquals(1178,r[1]);
  }
  @Test public void fallbackMovesToDifferentPlaceInsteadOfRetryingSamePoint(){
    int[] first=RewardExitPlanner.exitTap(720,1280,0,356,1185);
    int[] second=RewardExitPlanner.exitTap(720,1280,1,356,1185);
    assertNotEquals(first[0],second[0]);
    assertArrayEquals(new int[]{86,1178},second);
  }
  @Test public void scalesTo540x960Diagnostics(){
    int[] r=RewardExitPlanner.exitTap(540,960,0,-1,-1);
    assertEquals(276,r[0]);
    assertEquals(883,r[1]);
    assertTrue(RewardExitPlanner.promptLocation(540,960,r[0],r[1]));
  }
  @Test public void rejectsOutOfScreenDimensions(){
    try {RewardExitPlanner.exitTap(0,960,0,1,2);fail("Expected exception");}
    catch(IllegalArgumentException expected){}
  }
}
