package com.xnjtg.benben;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;
public class MainActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(28,28,28,28); root.setBackgroundColor(Color.rgb(247,249,252));
    TextView title=new TextView(this); title.setText("奔奔王国助手"); title.setTextSize(25); title.setTextColor(Color.rgb(18,44,72)); root.addView(title);
    TextView subtitle=new TextView(this); subtitle.setText("MuMu 独立 APK · V0.2 无障碍诊断"); subtitle.setTextSize(14); root.addView(subtitle);
    String[] tasks={"自动领取奖励","资源采集","自动打野怪","训练士兵","加入集结","每日任务","联盟帮助"};
    for(String task:tasks){Switch s=new Switch(this);s.setText(task);s.setTextSize(17);s.setPadding(0,15,0,15);s.setEnabled(false);root.addView(s);}
    Button enable=new Button(this);enable.setText("开启无障碍服务");enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(enable);
    Button info=new Button(this);info.setText("查看识别状态");info.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("无障碍诊断").setMessage("最近前台包名："+BenBenAccessibilityService.lastPackage+"\n事件类型："+BenBenAccessibilityService.lastEvent+"\n当前只读，不自动点击。请先启用服务，再切换到游戏。").setPositiveButton("知道了",null).show());root.addView(info);
    setContentView(root);
  }
}