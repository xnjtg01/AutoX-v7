package com.xnjtg.benben;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.provider.Settings;
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
  private static final String VERSION = "0.5.0";
  private static final String[] TASKS = {"自动领取奖励","资源采集","自动打野怪","训练士兵","加入集结","每日任务","联盟帮助"};
  private final StringBuilder records=new StringBuilder();
  private TextView status, logView;
  private ImageView preview;
  private Button target;
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
    if(records.length()>30000)records.delete(0,records.length()-20000);
    runOnUiThread(()->{status.setText(module+"："+message);logView.setText(records.toString());});
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
      +"note=本版本不上传任何日志。七项游戏任务尚未实现。\n"
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
    TextView sub=new TextView(this);sub.setText("V"+VERSION+" · 集中测试 / 错误收录\n仅诊断，不会自动操作游戏");sub.setTextSize(14);root.addView(sub);
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
    TextView tasksLabel=new TextView(this);
    tasksLabel.setText("\n七项任务：点击逐项检查当前实现状态（未完成任务不会执行）。");
    root.addView(tasksLabel);
    for(String name:TASKS){
      addButton(root,"测试 · "+name,v->record("NOT_IMPLEMENTED",name,
        "未实现游戏画面状态机、操作验证及完成条件；未执行游戏操作"));
    }
    addButton(root,"⑥ 一键运行全部安全诊断",v->task("全量诊断",()->{
      try{record("OK","全量诊断/Root",RootBridge.checkRoot());}
      catch(Exception e){record("FAIL","全量诊断/Root",e.toString());}
      record("INFO","全量诊断/无障碍",BenBenAccessibilityService.lastPackage);
      for(String name:TASKS)record("NOT_IMPLEMENTED",name,"暂不执行游戏操作");
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
    record("INFO","启动","版本 "+VERSION+" | 设备："+android.os.Build.MODEL+" | 诊断日志本地保存，绝不自动上传");
  }
}
