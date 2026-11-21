package com.xnjtg.benben;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.provider.Settings;
import android.net.Uri;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final String VERSION = "2.1.0";
  private static final String[] TASKS = {"自动领取奖励","资源采集","自动打野怪","训练士兵","加入集结","每日任务","联盟帮助"};
  private final StringBuilder records=new StringBuilder();
  private TextView status, logView;
  private ImageView preview;
  private Button target;
  private CaptureSession captureSession;
  private StatusOverlay overlay;
  private static final int EXPORT_ZIP_REQUEST=2026;
  private void task(String title, Runnable runnable) {
    new Thread(()->{
      try {runnable.run();}
      catch(Exception e){record("FAIL",title,e.getClass().getSimpleName()+": "+e.getMessage());}
    },"benben-"+title).start();
  }
  private String stamp(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());}
  private synchronized void record(String level,String module,String message){
    String line=stamp()+" | "+level+" | "+module+" | "+message+"\n";
    records.append(line);
    try(FileOutputStream autoLog=new FileOutputStream(new File(getFilesDir(),"benben-events.log"),true)) { autoLog.write(line.getBytes(StandardCharsets.UTF_8)); }
    catch(Exception ignored) { android.util.Log.w("BenBenAssistant","local event log write failed",ignored); }
    if(records.length()>30000)records.delete(0,records.length()-20000);
    runOnUiThread(()->{status.setText(module+"："+message);logView.setText(records.toString());});
    if(overlay!=null) overlay.update("奔奔助手 " + VERSION + "\n" + level+" / "+module+"\n"+(captureSession==null?"":("已采集 "+captureSession.frames()+" 帧")));
    android.util.Log.i("BenBenAssistant",line);
  }
  private Button addButton(LinearLayout parent,String label,View.OnClickListener callback){
    Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(callback);parent.addView(b);return b;
  }
  private File reportFile(){return new File(getExternalFilesDir(null),"benben-diagnostic-report.txt");}
  private String report(){
    return "奔奔王国助手 - 诊断报告\n"
      +"version="+VERSION+"\n"
      +"time="+stamp()+"\n"
      +"manufacturer="+android.os.Build.MANUFACTURER+"\n"
      +"model="+android.os.Build.MODEL+"\n"
      +"android="+android.os.Build.VERSION.RELEASE+"\n"
      +"screen="+getResources().getDisplayMetrics().widthPixels+"x"+getResources().getDisplayMetrics().heightPixels+"\n"
      +"lastAccessibilityPackage="+BenBenAccessibilityService.lastPackage+"\n"
      +"lastAccessibilityEvent="+BenBenAccessibilityService.lastEvent+"\n"
      +"note=本版本不上传日志。七项均含部分或单次源代码移植，尚未完成国内服实际验收。\n"
      +"---- events ----\n"+records.toString();
  }
  private void saveReport(){
    try(FileOutputStream out=new FileOutputStream(reportFile())){
      out.write(report().getBytes(StandardCharsets.UTF_8));
      record("OK","错误收录","报告已保存："+reportFile().getAbsolutePath());
    }catch(Exception e){record("FAIL","错误收录",e.toString());}
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,16,24,30);root.setBackgroundColor(Color.rgb(247,249,252));
    scroll.addView(root);
    TextView heading=new TextView(this);heading.setText("奔奔王国助手");heading.setTextSize(24);heading.setTextColor(Color.rgb(15,43,78));root.addView(heading);
    TextView sub=new TextView(this);sub.setText("V"+VERSION+" · 集中测试 / 错误收录\n独立 APK · 截图识别 + 弹窗处理 + 联盟互助单次尝试");sub.setTextSize(14);root.addView(sub);
    overlay=new StatusOverlay(this,()->{captureSession.stop();record("INFO","画面采样","用户在浮窗中点击停止");});
    // The floating stop panel is input-interactive: it MUST be removed before
    // any Root game tap, not just screenshots. Otherwise it consumes the
    // daily-missions top-right X (confirmed by user diagnostic captures).
    RootBridge.setTouchGuard(new RootBridge.TouchGuard(){
      @Override public void before()throws Exception{overlay.beforeScreenshot();}
      @Override public void after()throws Exception{overlay.afterScreenshot();}
    });
    captureSession=new CaptureSession(getExternalFilesDir(null),this::record,new CaptureSession.ScreenshotGuard(){
      public void before()throws Exception{overlay.beforeScreenshot();}
      public void after()throws Exception{overlay.afterScreenshot();}
    },new KingshotImageMatcher(getAssets()));
    status=new TextView(this);status.setText("等待测试");status.setPadding(0,12,0,12);root.addView(status);
    addButton(root,"① Root 权限检测",v->task("Root 检测",()->{
      try {record("OK","Root 检测",RootBridge.checkRoot());}
      catch(Exception e){record("FAIL","Root 检测",e.toString());}
    }));
    addButton(root,"② 5 秒后截图并识别（切换到游戏）",v->task("截图识别",()->{
      try{
        record("START","截图识别","5 秒后获取游戏屏幕");
        Thread.sleep(5000);
        File file=new File(getExternalFilesDir(null),"benben-screen.png");
        Bitmap bitmap=RootBridge.screenshot(file);
        String result=RootBridge.classify(bitmap);
        record("OK","截图识别",bitmap.getWidth()+"x"+bitmap.getHeight()+" | "+result+" | 保存："+file.getAbsolutePath());
        runOnUiThread(()->preview.setImageBitmap(bitmap));
      }catch(Exception e){record("FAIL","截图识别",e.toString());}
    }));
    target=addButton(root,"③ Root 安全点击目标（本应用按钮）",v->{
      record("OK","Root 点击","本应用按钮收到点击事件");
    });
    addButton(root,"运行 Root 点击测试",v->{
      int[] p=new int[2];target.getLocationOnScreen(p);
      if(target.getWidth()==0){record("FAIL","Root 点击","按钮未完成布局");return;}
      final int x=p[0]+target.getWidth()/2,y=p[1]+target.getHeight()/2;
      task("Root 点击",()->{
        try{record("OK","Root 点击",RootBridge.tap(x,y)+" | "+x+","+y);}
        catch(Exception e){record("FAIL","Root 点击",e.toString());}
      });
    });
    addButton(root,"④ 打开无障碍权限设置",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    addButton(root,"⑤ 检查无障碍记录",v->record("INFO","无障碍","包名="+BenBenAccessibilityService.lastPackage+
      "，事件="+BenBenAccessibilityService.lastEvent));
    TextView collection=new TextView(this);
    collection.setText("\nV2.1：根据国内服真实截图新增绿色握手快捷互助识别，不再依赖国际服联盟帮助模板；只点击一次，自动保存点击前后截图。其余七项容错测试与诊断功能保留。");
    root.addView(collection);
    addButton(root,"▶ 一键采样不同画面（5秒后切换游戏）",v->{
      if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
        record("INFO","悬浮窗","需要一次性授权“显示在其他应用上层”，授权后回助手再次点击开始采样");
        Intent permission=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:"+getPackageName()));
        startActivity(permission);
        return;
      }
      overlay.show("奔奔助手 V"+VERSION+"\n准备开始画面采样");
      if(!captureSession.start())record("INFO","画面采样","采样正在进行，或无法创建目录");
    });
    addButton(root,"★ 一键自动巡检城镇/联盟/野外（5秒后切换游戏）",v->{
      if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
        record("INFO","悬浮窗","请先授予浮窗权限");
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        return;
      }
      overlay.show("奔奔助手 V"+VERSION+"\n自动页面巡检准备中");
      if(!captureSession.startTour())record("INFO","自动巡检","已在运行采样，或无法创建目录");
    });
    addButton(root,"★ 一键执行：关闭确认弹窗＋联盟互助（单次）",v->{
      if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
        record("INFO","悬浮窗","请先授予浮窗权限");
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        return;
      }
      overlay.show("奔奔助手 V"+VERSION+"\n自动互助任务准备中");
      if(!captureSession.startAutoHelp())record("INFO","联盟互助","已有任务运行，未重复启动");
    });
    addButton(root,"★ Kingshot 奖励领取（邮件＋每日任务）",v->{
      if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
        record("INFO","悬浮窗","需要先授权悬浮窗以保证截图干净且可以停止");
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        return;
      }
      overlay.show("奔奔助手 V"+VERSION+"\n邮件奖励任务准备中");
      if(!captureSession.startRewards())record("INFO","邮件奖励","已有任务运行");
    });
    addButton(root,"▶ 一键测试全部七项（单项失败继续下一项）",v->{
      if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        return;
      }
      overlay.show("奔奔助手 V"+VERSION+"\n七项联合任务进行中");
      if(!captureSession.startAllTasks())record("INFO","七项任务","任务已在运行，不会重复启动");
    });
    final int[] marchTypes={KingshotMarchTasks.FARM,KingshotMarchTasks.HUNT,
      KingshotMarchTasks.TRAIN,KingshotMarchTasks.RALLY};
    final String[] marchLabels={"资源采集（石头一队）","自动打野怪（单目标）",
      "训练士兵（单队列）","加入集结（单次）"};
    for(int i=0;i<marchTypes.length;i++){
      final int type=marchTypes[i];
      final String title=marchLabels[i];
      addButton(root,"Kingshot 单项 · "+title,v->{
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
          startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
          return;
        }
        overlay.show("奔奔助手 V"+VERSION+"\n"+title+"执行中");
        if(!captureSession.startMarch(type))record("INFO",title,"另一个任务正在运行");
      });
    }
    addButton(root,"■ 停止画面采样",v->{
      captureSession.stop();
      overlay.update("奔奔助手 V"+VERSION+"\n正在停止采样...");
      record("INFO","画面采样","已请求停止，返回助手等待采样结束后可导出");
    });
    addButton(root,"⬇ 导出完整诊断 ZIP（日志＋游戏截图）",v->{
      if(captureSession.isRunning()){
        record("INFO","导出报告","请先停止采样或等待采样完成");return;
      }
      if(captureSession.frames()==0){
        record("INFO","导出报告","尚无截图，请先运行一键批量采样");return;
      }
      Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
      save.addCategory(Intent.CATEGORY_OPENABLE);
      save.setType("application/zip");
      save.putExtra(Intent.EXTRA_TITLE,"benben-diagnostic-"+stamp().replace(' ','_').replace(':','-')+".zip");
      startActivityForResult(save,EXPORT_ZIP_REQUEST);
    });
    addButton(root,"隐藏悬浮窗",v->overlay.hide());
    TextView tasksLabel=new TextView(this);
    tasksLabel.setText("\n七项任务：按 Kingshot 源码移植，仍需国内服实测。每次任务操作必须匹配图像，无法确认时不点击。");
    root.addView(tasksLabel);
    for(String name:TASKS){
      if("自动领取奖励".equals(name)){
        addButton(root,"测试 · 奖励领取（邮件＋每日任务）",v->{
          if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            return;
          }
          overlay.show("奔奔助手 V"+VERSION+"\n邮件奖励");
          if(!captureSession.startRewards())record("INFO","邮件奖励","已有任务运行");
        });
      }else if("资源采集".equals(name)||"自动打野怪".equals(name)||
            "训练士兵".equals(name)||"加入集结".equals(name)){
        final int type="资源采集".equals(name)?KingshotMarchTasks.FARM:
          "自动打野怪".equals(name)?KingshotMarchTasks.HUNT:
          "训练士兵".equals(name)?KingshotMarchTasks.TRAIN:KingshotMarchTasks.RALLY;
        addButton(root,"运行 · "+name+"（仅执行一次）",v->{
          if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            return;
          }
          overlay.show("奔奔助手 V"+VERSION+"\n"+name+"进行中");
          if(!captureSession.startMarch(type))record("INFO",name,"已有任务运行，拒绝重复启动");
        });
      }else if("联盟帮助".equals(name)){
        addButton(root,"测试 · 联盟帮助（单次实测）",v->{
          if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            return;
          }
          overlay.show("奔奔助手 V"+VERSION+"\n互助任务");
          if(!captureSession.startAutoHelp())record("INFO","联盟互助","当前任务还在运行");
        });
      } else {
        addButton(root,"尚未支持 · "+name,v->record("NOT_IMPLEMENTED",name,
          "需要额外的游戏任务页面素材与状态机，本 APK 不会对未知界面进行猜测点击"));
      }
    }
    addButton(root,"⑥ 一键运行全部安全诊断",v->task("全量诊断",()->{
      try{record("OK","全量诊断/Root",RootBridge.checkRoot());}
      catch(Exception e){record("FAIL","全量诊断/Root",e.toString());}
      record("INFO","全量诊断/无障碍",BenBenAccessibilityService.lastPackage);
      for(String name:TASKS)record("BUILT_UNVERIFIED",name,"Kingshot 源码对应的单次执行逻辑已接入，但尚未在国内服验证成功率；不保证完整挂机");
      record("OK","全量诊断","基础诊断结束；截图需要使用单独的 5 秒倒计时入口");
    }));
    addButton(root,"⑦ 复制完整错误报告",v->{
      ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("奔奔助手诊断",report()));
      Toast.makeText(this,"已复制诊断报告，可以直接粘贴发送",Toast.LENGTH_LONG).show();
    });
    addButton(root,"⑧ 保存错误报告 TXT",v->saveReport());
    TextView logs=new TextView(this);logs.setText("\n运行日志（无需手动记录）");root.addView(logs);
    logView=new TextView(this);logView.setTextIsSelectable(true);logView.setTextSize(12);root.addView(logView);
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(420);root.addView(preview);
    setContentView(scroll);
    try {
      File previous=new File(getFilesDir(),"benben-events.log");
      if(previous.isFile()) {
        byte[] history=java.nio.file.Files.readAllBytes(previous.toPath());
        int from=Math.max(0,history.length-18000);
        records.append(new String(history,from,history.length-from,StandardCharsets.UTF_8));
        logView.setText(records.toString());
      }
    }catch(Exception e){android.util.Log.w("BenBenAssistant","Cannot load prior log",e);}
    record("INFO","启动","版本 "+VERSION+" | 设备："+android.os.Build.MODEL+" | 诊断日志本地保存，绝不自动上传");
  }
  @Override protected void onDestroy(){
    if(captureSession!=null)captureSession.stop();
    RootBridge.setTouchGuard(null);
    if(overlay!=null)overlay.hide();
    super.onDestroy();
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request!=EXPORT_ZIP_REQUEST||result!=RESULT_OK||data==null||data.getData()==null)return;
    android.net.Uri uri=data.getData();
    record("START","导出报告","正在写入 ZIP");
    task("导出报告",()->{
      try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri)){
        if(stream==null)throw new Exception("系统未返回可写输出流");
        captureSession.export(stream,report());
        record("OK","导出报告","完整诊断包已导出，包含 "+captureSession.frames()+" 张截图。可以将 ZIP 文件上传聊天。");
      }catch(Exception e){record("FAIL","导出报告",e.toString());}
    });
  }
}
