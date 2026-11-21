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
  private final ChineseOcr ocr;
  private final DomesticScreenNavigation navigation;
  private final String resourceType;
  public KingshotMarchTasks(KingshotImageMatcher matcher,IO io,ChineseOcr ocr,String resourceType){
    this.matcher=matcher;this.io=io;this.ocr=ocr;
    this.resourceType=resourceType==null?"石材":resourceType;
    this.navigation=new DomesticScreenNavigation(new DomesticScreenNavigation.IO(){
      public Bitmap screenshot()throws Exception{return io.screenshot();}
      public void save(Bitmap b,String label)throws Exception{io.save(b,label);}
      public void event(String level,String module,String message){io.event(level,module,message);}
      public boolean stopped(){return io.stopped();}
    },ocr,matcher);
  }
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
  private boolean ensureMap()throws Exception{
    DomesticScreenNavigation.Page page=navigation.current();
    if(page==DomesticScreenNavigation.Page.MAP){
      msg("定位","OK","已确认在野外地图");
      return true;
    }
    if(!navigation.restoreCity()) {
      msg("定位","SKIP","无法从当前页面安全回到城镇，停止野外动作");
      return false;
    }
    if(quit())return false;
    msg("定位","INFO","点击城镇右下角「野外」(658,1230)，等待地图状态");
    RootBridge.tap(658,1230);
    // Domestic game uses a ~5-10 second white cloud transition when switching
    // world/city; V1.9 sampled frames 025-026 during this transition and
    // incorrectly marked both gathering and hunting as failed.
    for(int attempt=1;attempt<=10&&!quit();attempt++){
      sleep(1300);
      page=navigation.current();
      msg("定位","INFO","野外切换状态检查 "+attempt+"/10: "+page);
      if(page==DomesticScreenNavigation.Page.MAP) {
        msg("定位","OK","已验证进入野外地图，不再返回城镇");
        return true;
      }
      if(page==DomesticScreenNavigation.Page.TRANSITION ||
         page==DomesticScreenNavigation.Page.UNKNOWN ||
         (page==DomesticScreenNavigation.Page.CITY && attempt<=3)){
        // No taps during animation; allow the rendered game UI to settle.
        continue;
      }
      break;
    }
    msg("定位","SKIP","野外切换后仍无法确认地图，最终状态="+page+"；不点击搜索或出征");
    return false;
  }
  private boolean ensureCity()throws Exception{
    boolean ok=navigation.restoreCity();
    if(!ok)msg("定位","SKIP","无法确认城镇，停止后续操作");
    return ok;
  }
  public boolean run(int mode)throws Exception{
    if(mode==FARM)return gatherDomesticResource();
    if(mode==RALLY)return joinRally();
    if(mode==HUNT)return huntOne();
    if(mode==TRAIN)return trainOne();
    throw new IllegalArgumentException("未知任务 "+mode);
  }
  /**
   * FarmingTask adaptation for the user's Chinese domestic-server carousel.
   * The original "最强奔奔" APK already exposes 4 resource settings in script.uip:
   * 面包, 木材, 石材, 铁矿. The category strip must be swiped horizontally.
   */
  private boolean gatherDomesticResource()throws Exception{
    final String tag="资源采集";
    msg(tag,"START","国内服资源类别="+resourceType+"；搜索面板横向滑动＋中文 OCR，最多派遣一支队伍");
    if(!ensureMap())return false;
    if(!tap(tag,"farming/search-icon.png",.76,8,790,115,180))return false;
    DomesticResourcePicker picker=new DomesticResourcePicker(ocr,new DomesticResourcePicker.IO(){
      @Override public Bitmap screenshot()throws Exception{return io.screenshot();}
      @Override public void save(Bitmap bitmap,String label)throws Exception{io.save(bitmap,label);}
      @Override public void event(String level,String label,String detail){io.event(level,label,detail);}
      @Override public boolean stopped(){return quit();}
    });
    if(!picker.selectAndSearch(resourceType)) {
      msg(tag,"SKIP","未能通过横向列表定位并搜索 "+resourceType+"，不会继续出征");
      return false;
    }
    // Keep the original Kingshot flow for the post-search stage, but never
    // invoke gather/deploy if the expected user-action button is not confirmed.
    if(!tap(tag,"farming/gather-button.png",.76,257,592,207,72)){
      msg(tag,"SKIP","已搜索到 "+resourceType+"，但未匹配到地图上的“采集”按钮；已截图，跳过派兵");
      return false;
    }
    if(quit())return false;
    if(match(tag,"farming/no-troops.png",.72,0,180,720,1050).found){
      msg(tag,"SKIP","没有可出征士兵，不会派遣");
      return false;
    }
    if(match(tag,"farming/max-march.png",.75,200,250,310,170).found){
      msg(tag,"SKIP","行军队列已满，不会派遣");
      return false;
    }
    if(!tap(tag,"farming/deploy-button.png",.79,388,1159,315,107))return false;
    msg(tag,"INFO","已尝试派遣一支队伍采集 "+resourceType+"，是否成功需验证游戏行军队列");
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
  /** TroopTrainingTask.GetCurrentTroopStatuses + ProcessAllReadyTroops:
   * source MenuTriggerArea=(5,522,17,52); verify side-menu only AFTER opening;
   * source ready status areas are at 160/159/158 x 556/630/705.
   */
  private boolean trainOne() throws Exception {
    final String tag="训练士兵";
    msg(tag,"START","以 Kingshot TroopTrainingTask 为依据检查训练队列，仅处理一类完成/空闲士兵");
    if(!ensureCity())return false;
    if(quit())return false;
    RootBridge.tap(14,548);
    sleep(800);
    if(!match(tag,"trooptraining/side-menu.png",.76,0,180,650,750).found){
      // International source template contains English "Town/Wilderness".
      // Domestic server uses "城镇/野外"; verify same panel with Chinese OCR.
      Bitmap side=io.screenshot();
      boolean verified=false;
      try{
        String text=ChineseOcr.compact(ocr.read(side).allText);
        verified=(text.contains("城镇")&&text.contains("野外")) ||
          (text.toLowerCase(java.util.Locale.ROOT).contains("town")&&
           text.toLowerCase(java.util.Locale.ROOT).contains("wilderness"));
        msg(tag,"INFO","侧栏文字检测："+text+"，界面确认="+verified);
      }catch(Exception e){msg(tag,"WARN","侧栏OCR失败："+e.getMessage());}
      finally{side.recycle();}
      if(!verified){
        msg(tag,"SKIP","打开侧栏后未确认城镇/野外菜单，不再点击训练");
        return false;
      }
    }
    final int[][] status={{160,556,120,35},{159,630,130,40},{158,705,130,35}};
    final String[] types={"步兵","骑兵","弓兵"};
    int selected=-1;
    KingshotImageMatcher.Match idle=null;
    Bitmap menu=io.screenshot();
    try{
      ChineseOcr.Result scan=ocr.read(menu);
      msg(tag,"INFO","国内服侧栏OCR="+scan.allText);
      for(int i=0;i<status.length;i++){
        int[] rc=status[i];
        String raw=scan.inArea(rc[0]-20,rc[1]-12,rc[2]+60,rc[3]+28);
        String txt=ChineseOcr.compact(raw).toLowerCase(java.util.Locale.ROOT);
        boolean ready=(txt.contains("空闲")||txt.contains("已完成")||txt.contains("完成")||
                       txt.contains("idle")||txt.contains("ready")||txt.contains("complete"));
        boolean busy=(txt.contains("训练中")||txt.matches(".*[0-9]{1,2}:[0-9]{2}.*"));
        msg(tag,"INFO",types[i]+"队列文字="+raw+"，空闲="+ready+"，忙碌="+busy);
        if(ready&&!busy){selected=i;break;}
      }
    }catch(Exception e){
      msg(tag,"WARN","中文OCR失败，改用 Kingshot 现成 Idle 图片："+e.getMessage());
    }finally{menu.recycle();}
    if(selected<0){
      for(int i=0;i<status.length&&!quit();i++){
        int[] rc=status[i];
        KingshotImageMatcher.Match m=match(tag,"trooptraining/idle.png",.81,
          rc[0]-12,rc[1]-16,rc[2]+32,rc[3]+34);
        if(m.found){selected=i;idle=m;break;}
      }
    }
    if(selected<0){
      msg(tag,"SKIP","三类兵营均未识别到 Idle；可能正在训练或中文服文本不同，拒绝猜测");
      return false;
    }
    int clickX=idle==null?status[selected][0]+status[selected][2]/2:idle.x;
    int clickY=idle==null?status[selected][1]+status[selected][3]/2:idle.y;
    msg(tag,"INFO","匹配到"+types[selected]+"空闲状态，点击 "+clickX+","+clickY);
    RootBridge.tap(clickX,clickY);
    sleep(1000);
    if(quit())return false;
    // V1.7 diagnostics showed '步兵已完成' but the following Train image
    // scored only 0.729 (< 0.79). Save the actual post-tap screen instead
    // of reducing the confidence threshold and risking a wrong tap.
    Bitmap opened=io.screenshot();
    try {
      io.save(opened,"训练-"+types[selected]+"-点击完成状态后");
      try{
        String afterText=ChineseOcr.compact(ocr.read(opened).allText);
        msg(tag,"INFO","点击已完成队列后的界面文字="+afterText);
      }catch(Exception e){msg(tag,"WARN","训练弹窗 OCR 无法读取："+e.getMessage());}
    }finally{opened.recycle();}
    KingshotImageMatcher.Match train=match(tag,"trooptraining/train.png",.79,70,225,600,820);
    if(!train.found){
      msg(tag,"SKIP","未找到 Train 图标，不会触发训练");
      return false;
    }
    if(!tap(tag,"trooptraining/train.png",.79,70,225,600,820))return false;
    // In the source the minus control is used as evidence of the quantity dialog.
    if(!match(tag,"trooptraining/minus-button.png",.75,0,170,720,850).found){
      msg(tag,"SKIP","未确认数量调节对话框，未提交训练");
      return false;
    }
    if(quit())return false;
    msg(tag,"INFO","已确认训练数量对话框；按源代码训练确认框中心(497,1107)提交一次");
    RootBridge.tap(497,1107);
    sleep(1150);
    Bitmap after=io.screenshot();
    try{io.save(after,"训练-"+types[selected]+"-提交后");}
    finally{after.recycle();}
    msg(tag,"INFO","训练操作已执行；是否实际开始需要核对建筑训练倒计时");
    return true;
  }
}
