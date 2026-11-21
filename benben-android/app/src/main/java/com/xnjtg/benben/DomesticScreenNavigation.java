package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Domestic Kingshot page recovery, grounded on the user's 2026-11-20
 * 720x1280 diagnostic captures. Never navigates without checking a page first.
 */
public final class DomesticScreenNavigation {
  public enum Page { CITY, MAP, MAIL, MISSIONS, ALLIANCE, ALLIANCE_WAR, WELCOME, REWARD, MARCH_SETUP, RESOURCE_DETAIL, RESOURCE_SEARCH, TRANSITION, UNKNOWN }
  public interface IO {
    Bitmap screenshot()throws Exception;
    void save(Bitmap bitmap,String label)throws Exception;
    void event(String level,String module,String message);
    boolean stopped();
  }
  private final IO io;
  private final ChineseOcr ocr;
  private final KingshotImageMatcher matcher;
  public DomesticScreenNavigation(IO io,ChineseOcr ocr,KingshotImageMatcher matcher){
    this.io=io;this.ocr=ocr;this.matcher=matcher;
  }
  private int sample(Bitmap b,float x,float y){
    return b.getPixel(Math.max(0,Math.min(b.getWidth()-1,Math.round((b.getWidth()-1)*x))),
      Math.max(0,Math.min(b.getHeight()-1,Math.round((b.getHeight()-1)*y))));
  }
  public boolean missionPanel(Bitmap b) {
    int blue=sample(b,.5f,.157f);
    boolean blueBar=Color.blue(blue)>Color.red(blue)+34 &&
        Color.green(blue)>Color.red(blue)+30;
    int left=sample(b,.25f,.875f),right=sample(b,.68f,.875f);
    boolean leftPale=Color.red(left)>210&&Color.green(left)>200&&Color.blue(left)>185;
    boolean rightPale=Color.red(right)>210&&Color.green(right)>200&&Color.blue(right)>185;
    boolean leftBrown=Color.red(left)>105&&Color.red(left)<185&&Color.green(left)<150;
    boolean rightBrown=Color.red(right)>105&&Color.red(right)<185&&Color.green(right)<150;
    // Growth page has a cyan heading; daily page may change its heading, but
    // retains the inverted two-tab palette at the same fixed bottom positions.
    return (blueBar&&leftPale&&rightBrown)||(rightPale&&leftBrown);
  }
  public boolean mailPanel(Bitmap b) {
    int middle=sample(b,.50f,.175f),top=sample(b,.5f,.03f),bottom=sample(b,.5f,.96f);
    boolean ivory=Color.red(middle)>225&&Color.green(middle)>214&&Color.blue(middle)>198;
    boolean header=Color.red(top)<150&&Color.green(top)<135&&Color.blue(top)<120;
    boolean footer=Color.red(bottom)>120&&Color.red(bottom)<200&&Color.green(bottom)<160;
    return ivory&&header&&footer;
  }
  /** Screenshot-derived screen checks from V1.7 frames 011-013 (720x1280).
   * Alliance home has a vivid green progress bar; alliance war has a pale
   * empty rally panel. Both have the same cyan back arrow and brown title bar.
   */
  private boolean allianceHeader(Bitmap b){
    int arrow=sample(b,40f/720f,39f/1280f);
    int header=sample(b,400f/720f,40f/1280f);
    return Color.green(arrow)>Color.red(arrow)+30
       && Color.blue(arrow)>Color.red(arrow)+15
       && Color.red(header)>=55 && Color.red(header)<=165
       && Color.green(header)>=48 && Color.green(header)<=135
       && Color.blue(header)>=32 && Color.blue(header)<=120;
  }
  private boolean allianceHome(Bitmap b){
    if(!allianceHeader(b))return false;
    int bar=sample(b,300f/720f,390f/1280f);
    int action=sample(b,200f/720f,1050f/1280f);
    return Color.green(bar)>=150 && Color.green(bar)>Color.red(bar)+65
       && Color.green(bar)>Color.blue(bar)+30
       && Color.red(action)>Color.blue(action)+70
       && Color.red(action)>Color.green(action)+15;
  }
  private boolean allianceWar(Bitmap b){
    if(!allianceHeader(b))return false;
    int center=sample(b,300f/720f,390f/1280f);
    int empty=sample(b,200f/720f,930f/1280f);
    return paleBeige(center)&&paleBeige(empty);
  }
  private boolean paleBeige(int c){
    int r=Color.red(c),g=Color.green(c),blue=Color.blue(c);
    return r>=218&&r<=255&&g>=207&&g<=249&&blue>=175&&blue<=235
      && r>g&&g>blue;
  }
  /** User's V1.9 frames 025-026 show a pale blue/white cloud during map
   * switching. This is an animation, NOT an unknown interactive page.
   * Never send Back or tap another button while a transition is visible.
   */
  public boolean mapTransition(Bitmap b){
    float[][] samples={{.15f,.20f},{.5f,.20f},{.85f,.22f},{.22f,.48f},
      {.5f,.55f},{.78f,.62f},{.25f,.82f},{.72f,.87f}};
    int cloud=0;
    for(float[] point:samples){
      int c=sample(b,point[0],point[1]);
      int r=Color.red(c),g=Color.green(c),blue=Color.blue(c);
      if(r>=204 && g>=211 && blue>=211 && blue>=g-11 && g>=r-14)cloud++;
    }
    return cloud>=7;
  }
  /** Distinguish the turquoise bottom-right "野外" city shortcut from the
   * world map's castle shortcut. Both modes may share a beige toolbar.
   * Calibrated against V1.9 frames 017 and 027: turquoise pixel share ~0.26.
   */
  private boolean cityWorldShortcut(Bitmap b){
    int turquoise=0,total=0;
    for(int xi=0;xi<12;xi++)for(int yi=0;yi<7;yi++){
      float x=.82f+(xi+.5f)*.17f/12f;
      float y=.94f+(yi+.5f)*.047f/7f;
      int c=sample(b,x,y);
      total++;
      if(Color.green(c)>Color.red(c)+18 &&
        Color.blue(c)>Color.red(c)+7)turquoise++;
    }
    return turquoise>=17;
  }
  public Page detect(Bitmap b) {
    if(mapTransition(b))return Page.TRANSITION;
    if(VisualEngine.welcome(b))return Page.WELCOME;
    if(missionPanel(b))return Page.MISSIONS;
    if(mailPanel(b))return Page.MAIL;
    if(allianceHome(b))return Page.ALLIANCE;
    if(allianceWar(b))return Page.ALLIANCE_WAR;
    // The user's V2.2 diagnostic frames 024-030 show the turquoise wilderness
    // search button persists behind the resource detail popup. This is not
    // UNKNOWN and must be closed in two stages before returning to the city.
    if(DomesticResourcePicker.searchPanelVisible(b)){
      int l=sample(b,.21f,.30f),r=sample(b,.79f,.30f);
      boolean popup=Color.red(l)>235&&Color.green(l)>228&&Color.blue(l)>220&&
        Color.red(r)>235&&Color.green(r)>228&&Color.blue(r)>220;
      return popup?Page.RESOURCE_DETAIL:Page.RESOURCE_SEARCH;
    }
    // OCR is used for page identity only; any unknown text is not a click target.
    try {
      ChineseOcr.Result scanned=ocr.read(b);
      String all=ChineseOcr.compact(scanned.allText);
      // Chinese troop dispatch page observed in V2.4 frame_028.
      if(all.contains("出征") &&
        (all.contains("王牌步兵")||all.contains("王牌骑兵")||all.contains("王牌弓兵")) &&
        (all.contains("石材")||all.contains("木材")||all.contains("面包")||all.contains("铁矿")))
        return Page.MARCH_SETUP;
      if(all.contains("欢迎回来") && (all.contains("离线")||all.contains("收益")))return Page.WELCOME;
      if(all.contains("获得奖励") && (all.contains("点击任意")||all.contains("退出")))return Page.REWARD;
      if(all.contains("成长任务")&&(all.contains("主线任务")||all.contains("每日任务")))return Page.MISSIONS;
      if(all.contains("邮件")&&all.contains("联盟")&&all.contains("系统"))return Page.MAIL;
      // Secondary fallback for busy alliance-war pages where the central
      // empty-panel color is replaced by an active rally entry.
      if(all.contains("联盟战争")&&all.contains("集结")&&all.contains("单人"))
        return Page.ALLIANCE_WAR;
      if(all.contains("联盟战争")&&
         (all.contains("联盟互助")||all.contains("联盟领地")||all.contains("联盟商店")))
        return Page.ALLIANCE;
      if(VisualEngine.city(b)){
        String bottom=ChineseOcr.compact(scanned.inArea(
          (int)(b.getWidth()*.76f),(int)(b.getHeight()*.89f),
          (int)(b.getWidth()*.24f),(int)(b.getHeight()*.11f)));
        if(bottom.contains("野外"))return Page.CITY;
        if(bottom.contains("城镇"))return Page.MAP;
      }
    }catch(Exception e){
      io.event("WARN","页面识别","OCR 无法判断页面："+e.getMessage());
    }
    if(VisualEngine.city(b)) {
      try {
        // City reference is the map shortcut displayed at lower-right in city view.
        if(matcher.findInReferenceArea(b,"locator/base-view.png",.60,562,1120,158,160).found)
          return Page.CITY;
        // Wilderness map shows a castle/城镇 shortcut at lower-right.
        if(matcher.findInReferenceArea(b,"locator/map-view.png",.62,562,1120,158,160).found)
          return Page.MAP;
      }catch(Exception e){
        io.event("WARN","页面识别","底栏识别异常："+e.getMessage());
      }
    }
    // The city footer is stable even when the camera is over an empty grassy
    // area. V1.9 frame 027 has no recognizable buildings, but has the same
    // six brown footer anchors as frame 017 and clearly shows the "野外" tab.
    // City marker recognition must not depend on buildings being present.
    if(VisualEngine.city(b) && cityWorldShortcut(b))return Page.CITY;
    return Page.UNKNOWN;
  }
  public Page current()throws Exception {
    Bitmap b=io.screenshot();
    try {return detect(b);}
    finally {b.recycle();}
  }
  private void note(String level,String message){io.event(level,"页面恢复",message);}
  private void pause()throws InterruptedException{Thread.sleep(1000);}
  private void tap(Bitmap b,int refX,int refY)throws Exception{
    int x=Math.round(refX*b.getWidth()/720f);
    int y=Math.round(refY*b.getHeight()/1280f);
    RootBridge.tap(x,y);
  }
  public boolean restoreCity()throws Exception{
    int welcomeClicks=0, missionCloses=0, mailCloses=0, rewardCloses=0, unknownBacks=0, allianceBacks=0, transitionWaits=0, resourceDetailBacks=0, resourcePanelBacks=0, marchSetupBacks=0;
    for(int attempts=0;attempts<16&&!io.stopped();attempts++) {
      Bitmap b=io.screenshot();
      Page page;
      try {
        page=detect(b);
        note("INFO","当前页面="+page+"，恢复检查="+(attempts+1)+"/16");
        if(page==Page.CITY)return true;
        if(page==Page.TRANSITION){
          transitionWaits++;
          if(transitionWaits>10){
            io.save(b,"恢复失败-地图加载动画超时");
            note("FAIL","地图切换动画持续超过10轮，放弃当前场景，不会盲点");
            return false;
          }
          note("INFO","正在切换城镇/野外，检测到白云动画，等待界面稳定 (第"+transitionWaits+"/10次)");
          Thread.sleep(1150);
          continue;
        }
        transitionWaits=0;
        if(page==Page.MISSIONS){
          if(missionCloses>=2){
            io.save(b,"失败-每日任务关闭两次未成功");
            note("FAIL","已尝试关闭每日任务两次，仍检测到任务面板，停止触摸");
            return false;
          }
          io.save(b,"恢复-发现每日任务弹窗");
          // From user's 720x1280 screenshots, X is at (687,125).
          // The global tap guard MUST hide the floating control first.
          tap(b,687,125);
          missionCloses++;
          note("INFO","已在隐藏浮窗后点击任务面板关闭 x=687 y=125");
        }else if(page==Page.MAIL){
          if(mailCloses>=2){note("FAIL","邮件窗口连续两次关闭失败");return false;}
          io.save(b,"恢复-发现邮件页面");
          tap(b,685,43);
          mailCloses++;
          note("INFO","已在隐藏浮窗后点击邮件页面关闭");
        }else if(page==Page.ALLIANCE || page==Page.ALLIANCE_WAR){
          // Evidence: V1.7 frames 012 and 013, cyan top-left back arrow.
          // Android Back returned to alliance home but V1.7 previously
          // classified that screen UNKNOWN and aborted the entire task queue.
          if(allianceBacks>=3){
            io.save(b,"恢复失败-联盟页面");
            note("FAIL","联盟页面返回三次仍未回城镇，安全停止");
            return false;
          }
          io.save(b,page==Page.ALLIANCE_WAR?
            "恢复-退出联盟战争":"恢复-退出联盟主页");
          tap(b,48,40);
          allianceBacks++;
          note("INFO","已点击"+(page==Page.ALLIANCE_WAR?"联盟战争":"联盟主页")
            +"左上角返回，次数="+allianceBacks);
        }else if(page==Page.MARCH_SETUP){
          if(marchSetupBacks>=2){
            io.save(b,"恢复失败-出征页无法退出");
            note("FAIL","出征配置页已返回两次仍未退出，停止后续未知页面点击");
            return false;
          }
          io.save(b,"恢复-出征配置页面");
          // A backward navigation cancels the form without sending a march.
          RootBridge.back();marchSetupBacks++;
          note("INFO","识别到国内服兵力配置/出征页面，安全返回，不点击出征");
        }else if(page==Page.RESOURCE_DETAIL || page==Page.RESOURCE_SEARCH){
          if(page==Page.RESOURCE_DETAIL){
            if(resourceDetailBacks>=2){
              io.save(b,"恢复失败-石材详情无法关闭");
              note("FAIL","资源详情弹窗两次返回仍未关闭；停止尝试");
              return false;
            }
            io.save(b,"恢复-石材详情弹窗");
            RootBridge.back();resourceDetailBacks++;
            note("INFO","识别到采集资源详情，先执行返回以关闭详情弹窗");
          }else{
            if(resourcePanelBacks>=2){
              io.save(b,"恢复失败-资源搜索面板无法关闭");
              note("FAIL","资源搜索面板两次返回仍未关闭；不乱点地图");
              return false;
            }
            io.save(b,"恢复-资源搜索面板");
            RootBridge.back();resourcePanelBacks++;
            note("INFO","识别到野外横向资源搜索面板，执行返回关闭，随后恢复城镇");
          }
        }else if(page==Page.MAP){
          tap(b,658,1230);
          note("INFO","已点击野外地图右下角城镇入口");
        }else if(page==Page.WELCOME){
          if(welcomeClicks>=2){
            io.save(b,"恢复失败-欢迎回来");
            note("FAIL","欢迎回来弹窗两次确认后仍在，停止点击");
            return false;
          }
          io.save(b,"恢复-欢迎回来确认前");
          // Matches the center of the actual green confirm button (0.50, 0.802).
          tap(b,360,1027);
          welcomeClicks++;
          note("INFO","已点击欢迎回来绿色确定，随后会重新截图验证");
        }else if(page==Page.REWARD){
          // V2.3 regression: clicking (360,960) hit the MIDDLE of the reward
          // grid, not the bottom "点击任意位置退出" instruction. V2.3 stayed
          // on this same screenshot for >10 taps, blocking the other 6 tasks.
          // Autopilot's Kingshot reference places the prompt at y=90%..94%.
          if(rewardCloses>=3){
            io.save(b,"奖励弹窗-退出失败");
            note("FAIL","三种独立的奖励弹窗退出方式均失败；停止点击并记录本项受阻");
            return false;
          }
          io.save(b,"恢复-获得奖励弹窗-尝试"+(rewardCloses+1));
          if(rewardCloses<2){
            int ocrX=-1,ocrY=-1;
            try{
              ChineseOcr.Result prompt=ocr.read(b);
              for(ChineseOcr.Item line:prompt.lines){
                if(line.box==null)continue;
                String val=ChineseOcr.compact(line.text);
                int x=line.box.centerX(),y=line.box.centerY();
                if(val.contains("点击任意")&&val.contains("退出")&&
                   RewardExitPlanner.promptLocation(b.getWidth(),b.getHeight(),x,y)){
                  ocrX=x;ocrY=y;break;
                }
              }
            }catch(Exception e){note("WARN","奖励退出提示 OCR 定位失败，采用校准坐标："+e.getMessage());}
            int[] target=RewardExitPlanner.exitTap(b.getWidth(),b.getHeight(),rewardCloses,ocrX,ocrY);
            RootBridge.tap(target[0],target[1]);
            note("INFO","奖励退出 "+(rewardCloses+1)+"/3：点击底部提示位置 ("
              +target[0]+","+target[1]+")，不是物品格");
          }else{
            // Last fallback: Android Back is only allowed after confirming the
            // rewards overlay is STILL on screen.
            RootBridge.back();
            note("INFO","奖励退出 3/3：仍确认是奖励弹窗，尝试一次系统返回");
          }
          rewardCloses++;
        }else{
          if(unknownBacks<1) {
            RootBridge.back();
            unknownBacks++;
            note("INFO","未知页面仅尝试一次系统返回");
          }else {
            io.save(b,"恢复失败-未知页面");
            note("FAIL","无法判断界面，停止后续任务");
            return false;
          }
        }
      }finally {b.recycle();}
      // Rendering and animation have to settle before verifying each action.
      Thread.sleep(950);
    }
    if(!io.stopped()){
      Bitmap end=io.screenshot();
      try{io.save(end,"恢复失败-最终画面");}finally{end.recycle();}
    }
    note("FAIL","限定次数内未能回到城镇，后续任务停止");
    return false;
  }
}
