package com.xnjtg.benben;

/**
 * Pure geometry for the Kingshot domestic rewards popup.
 * The public MIT-licensed Autopilot project locates the "tap anywhere to exit"
 * prompt in a normalized image rectangle:
 * x 31.94%..70.14%, y 90.3125%..93.5156%.
 * This is a bottom prompt, not the center reward grid.
 *
 * The crop and coordinates are from upstream's
 * games/kingshot/core/rewards/area.yaml (checked 2026-11-21).
 */
public final class RewardExitPlanner {
  private RewardExitPlanner() {}
  public static final float PROMPT_TOP=.903125f;
  public static final float PROMPT_BOTTOM=.935156f;
  public static final float PROMPT_CENTER_X=.51f;
  public static final float PROMPT_CENTER_Y=.92f;

  public static int[] exitTap(int width,int height,int attempt,int ocrX,int ocrY) {
    if(width<=0||height<=0)throw new IllegalArgumentException("Invalid screen");
    if(attempt==0 && ocrX>=width*.20f && ocrX<=width*.80f &&
       ocrY>=height*.87f && ocrY<=height*.97f) {
      return new int[]{ocrX,ocrY};
    }
    // First fallback: center of the verified bottom prompt region.
    if(attempt<=0)return new int[]{Math.round(width*PROMPT_CENTER_X),
       Math.round(height*PROMPT_CENTER_Y)};
    // Second fallback: dark overlay to the left of the prompt, away from
    // resource items; only after the reward state is re-confirmed.
    return new int[]{Math.round(width*.12f),Math.round(height*.92f)};
  }
  public static boolean promptLocation(int width,int height,int x,int y) {
    return x>=width*.18f&&x<=width*.82f&&
      y>=height*.87f&&y<=height*.97f;
  }
}