package com.xnjtg.benben;

import android.graphics.Bitmap;

/**
 * Source-guided one-cycle game tasks derived from KingshotAuto/Kingshot-bot (MIT).
 * Designed for 720x1280 Android screenshots; all coordinates are scaled from
 * the upstream C# search rectangles, but actions are gated by image matches.
 *
 * No paid consumables, no unbounded loops, no blind retries.
 */
public final class KingshotMarchTasks {
  public static final int FARM=1, RALLY=2, HUNT=3, TRAIN=4;
  public interface IO {
    Bitmap screenshot() throws Exception;
    void save(Bitmap bitmap,String step) throws Exception;
    void event(String level,String module,String message);
    boolean stopped();
  }
  private final KingshotImageMatcher matcher;
  private final IO io;
  public KingshotMarchTasks(KingshotImageMatcher matcher,IO io){this.matcher=matcher;this.io=io;}
  private void msg(String scope,String severity,String message){
    io.event(severity,scope,message);
  }
  private boolean quit(){return io.stopped()||Thread.currentThread().isInterrupted();}
  private void sleep(long ms)throws Exception{
    long end=System.currentTimeMillis()+ms;
    while(System.currentTimeMillis()<end){
      if(quit())throw new InterruptedException("任务已停止");
      Thread.sleep(Math.min(200,end-System.currentTimeMillis()));
    }
  }
  private KingshotImageMatcher.Match match(String scope,String image,double threshold,
       int x,int y,int w,int h)throws Exception {
    if(quit())throw new InterruptedException("任务已停止");
    Bitmap b=io.screenshot();
    try {
      KingshotImageMatcher.Match found=matcher.findInReferenceArea(b,image,threshold,x,y,w,h);
      msg(scope,"INFO","模板检测 "+found);
      return found;
    } finally {b.recycle();}
  }
  private boolean tap(String scope,String image,double threshold,
       int x,int y,int w,int h)throws Exception {
    KingshotImageMatcher.Match m=match(scope,image,threshold,x,y,w,h);
    if(!m.found){
      msg(scope,"SKIP","未识别 "+image+"，不执行任何猜测点击");
      return false;
    }
    if(quit())return false;
    msg(scope,"INFO","即将点击 "+image+"，坐标="+m.x+","+m.y);
    RootBridge.tap(m.x,m.y);
    sleep(1050);
    Bitmap after=io.screenshot();
    try {io.save(after,scope+"_"+image.replace('/','_').replace(".png","")+"_点击后");}
    finally {after.recycle();}
    return true;
  }
  private boolean isView(String asset,double confidence)throws Exception{
    KingshotImageMatcher.Match m=match("定位",asset,confidence,0,0,720,1280);
    return m.found;
  }
  private boolean ensureMap() throws Exception {
    if(quit())return false;
    if(isView("locator/map-view.png",.72)) {
      msg("定位","OK","已经位于野外地图");
      return true;
    }
    Bitmap city=io.screenshot();
    boolean valid;
    try{valid=VisualEngine.city(city);io.save(city,"切换野外前");}
    finally{city.recycle();}
    if(!valid && !isView("locator/base-view.png",.75)){
      msg("定位","SKIP","无法判断城镇或地图页面，停止");
      return false;
    }
    // Exact bottom-right wilderness/野外 tab is user-confirmed in domestic screenshot.
    if(quit())return false;
    msg("定位","INFO","点击已校准的城镇底部“野外”导航");
    RootBridge.tap(658,1230);
    sleep(1600);
    if(isView("locator/map-view.png",.68))return true;
    msg("定位","SKIP","跳转后未能识别野外标记；中止任务，避免误触");
    return false;
  }
  private boolean ensureCity()throws Exception{
    Bitmap b=io.screenshot();
    try {
      boolean city=VisualEngine.city(b)&&
        matcher.findInReferenceArea(b,"locator/base-view.png",.68,0,0,720,1280).found;
      if(!city)msg("定位","SKIP","未确认城镇主界面，停止");
      return city;
    }finally{b.recycle();}
  }
  public boolean run(int mode)throws Exception{
    if(mode==FARM)return farmStone();
    if(mode==RALLY)return joinRally();
    if(mode==HUNT)return huntOne();
    if(mode==TRAIN)return trainOne();
    throw new IllegalArgumentException("未知任务 "+mode);
  }
  /** FarmingTask.ProcessSingleMarchAsync - stone, one march, default current level. */
  private boolean farmStone()throws Exception{
    final String tag="资源采集";
    msg(tag,"START","参考 Kingshot FarmingTask：石头资源，最多派遣一支队伍");
    if(!ensureMap())return false;
    if(!tap(tag,"farming/search-icon.png",.76,8,790,115,180))return false;
    if(!tap(tag,"farming/stone.png",.76,0,835,720,225))return false;
    // The upstream search button is a coordinate-based action only after resource selection.
    if(quit())return false;
    msg(tag,"INFO","已选择石头。点击源码搜索区域(117,1166,414,102)");
    RootBridge.tap(324,1217);
    sleep(1400);
    if(!tap(tag,"farming/gather-button.png",.76,257,592,207,72))return false;
    if(quit())return false;
    if(match(tag,"farming/no-troops.png",.72,0,180,720,1050).found) {
      msg(tag,"SKIP","没有可出征士兵，不会派遣");
      return false;
    }
    if(match(tag,"farming/max-march.png",.75,200,250,310,170).found){
      msg(tag,"SKIP","行军队列已满，不会派遣");
      return false;
    }
    if(!tap(tag,"farming/deploy-button.png",.79,388,1159,315,107))return false;
    msg(tag,"OK","已经执行单次出征操作（必须以实际队列状态确认派遣结果）");
    return true;
  }
  /** AutoRallyTask.TryJoinRallyAsync - at most one join, no auto repeat. */
  private boolean joinRally()throws Exception {
    final String tag="加入集结";
    msg(tag,"START","参考 Kingshot AutoRallyTask：仅加入一次，不开启自动集结常驻开关");
    if(!ensureCity())return false;
    boolean opened=tap(tag,"Auto Rally/rally-icon.png",.75,570,445,150,190);
    if(!opened){
      // Upstream fallback: open alliance and then war if direct rally icon is missing.
      if(!tap(tag,"Auto Rally/alliance-icon.png",.77,0,1020,720,260))return false;
      if(!tap(tag,"Auto Rally/war-icon.png",.77,0,60,720,1130))return false;
    }
    if(!match(tag,"Auto Rally/rally-menu.png",.72,0,0,720,1280).found){
      msg(tag,"SKIP","集结列表界面未被识别，不会乱点加号");
      return false;
    }
    if(!tap(tag,"Auto Rally/green-plus.png",.80,572,164,146,1110)){
      msg(tag,"INFO","当前没有匹配到可加入的集结");
      return false;
    }
    if(quit())return false;
    if(match(tag,"Auto Rally/max-march.png",.75,0,0,720,1280).found){
      msg(tag,"SKIP","已达最大行军数量");
      return false;
    }
    if(!tap(tag,"Auto Rally/equalize.png",.77,0,0,720,1280))return false;
    if(!tap(tag,"Auto Rally/deploy.png",.81,330,1020,390,260))return false;
    if(quit())return false;
    if(match(tag,"Auto Rally/confirm-join.png",.75,0,0,720,1280).found){
      msg(tag,"WARN","发现集结确认或错误弹窗，停止，不盲点确认");
      return false;
    }
    boolean list=match(tag,"Auto Rally/rally-menu.png",.72,0,0,720,1280).found;
    msg(tag,list?"OK":"WARN",list?"返回集结列表，疑似加入成功":"未确认返回集结列表，不能认定成功");
    return list;
  }
  /** AutoHuntCoordinator: enter hunt mode and attack a matched bear target. */
  private boolean huntOne()throws Exception {
    final String tag="打野怪";
    msg(tag,"START","参考 Kingshot AutoHuntCoordinator：只尝试一只可识别的目标");
    if(!ensureMap())return false;
    if(!tap(tag,"autohunt/hunt-button.png",.77,618,808,92,278))return false;
    if(!match(tag,"autohunt/castle-hunt.png",.76,0,110,700,1050).found){
      msg(tag,"SKIP","没有确认进入狩猎界面");return false;
    }
    if(!tap(tag,"autohunt/bear.png",.79,0,110,700,1050)){
      msg(tag,"SKIP","没有找到熊类目标；不会点击其他不认识的怪物");return false;
    }
    if(!tap(tag,"autohunt/view-button.png",.77,0,120,720,1150))return false;
    if(!tap(tag,"autohunt/attack-button.png",.77,0,120,720,1150))return false;
    if(match(tag,"autohunt/stamina-low.png",.75,200,50,360,230).found) {
      msg(tag,"SKIP","体力不足，不出征");return false;
    }
    // Unlike upstream, don't blindly dismiss a potential defeat predictor
    // without OCR confirmation.
    if(match(tag,"autohunt/confirmation.png",.76,0,120,720,1100).found){
      msg(tag,"WARN","发现尚未解释的确认对话框，停止本次自动打野");
      return false;
    }
    KingshotImageMatcher.Match equal=match(tag,"autohunt/equalize.png",.75,0,250,720,780);
    if(equal.found){
      if(!tap(tag,"autohunt/equalize.png",.75,0,250,720,780))return false;
    }
    if(match(tag,"autohunt/max-march.png",.75,0,200,720,500).found){
      msg(tag,"SKIP","行军队列已满，停止");return false;
    }
    if(!tap(tag,"autohunt/deploy-button.png",.82,388,1159,315,107))return false;
    msg(tag,"INFO","已执行狩猎出征，战斗胜负未知，保存截图供核对");
    return true;
  }
  /** TroopTrainingTask: one idle queue, source train & minus templates. */
  private boolean trainOne()throws Exception{
    final String tag="训练士兵";
    msg(tag,"START","参考 Kingshot TroopTrainingTask：只处理一个已识别空闲训练队列");
    if(!ensureCity())return false;
    if(!tap(tag,"trooptraining/side-menu.png",.78,0,180,350,900))return false;
    if(!tap(tag,"trooptraining/idle.png",.80,60,440,320,480)){
      msg(tag,"SKIP","没有可信的空闲队列，不会重复训练");return false;
    }
    // Upstream checks train.png and minus-button.png before a final train-box click.
    if(!tap(tag,"trooptraining/train.png",.78,120,270,570,800))return false;
    if(!match(tag,"trooptraining/minus-button.png",.77,0,180,720,870).found){
      msg(tag,"SKIP","没有进入训练数量界面，未提交训练");return false;
    }
    if(quit())return false;
    msg(tag,"INFO","已识别训练数量控件，按源项目最终训练框 (409,1078,177,58) 单次提交");
    RootBridge.tap(497,1106);
    sleep(1200);
    Bitmap verify=io.screenshot();
    try{io.save(verify,"训练-提交后画面");}
    finally{verify.recycle();}
    msg(tag,"INFO","训练提交点击已执行，是否开始训练需以建筑队列状态确认");
    return true;
  }
}
