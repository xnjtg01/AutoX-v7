package com.xnjtg.benben;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
public class MainActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(28,28,28,28); root.setBackgroundColor(Color.rgb(247,249,252));
    TextView title=new TextView(this); title.setText("奔奔王国助手"); title.setTextSize(25); title.setTextColor(Color.rgb(18,44,72)); root.addView(title);
    TextView subtitle=new TextView(this); subtitle.setText("MuMu 独立 APK · 识别与自动化功能开发中"); subtitle.setTextSize(14); root.addView(subtitle);
    String[] tasks={"自动领取奖励","资源采集","自动打野怪","训练士兵","加入集结","每日任务","联盟帮助"};
    for(String task:tasks){Switch s=new Switch(this);s.setText(task);s.setTextSize(17);s.setPadding(0,15,0,15);s.setEnabled(false);root.addView(s);}
    Button info=new Button(this);info.setText("查看当前状态");info.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("开发预览版").setMessage("本 APK 可以独立安装和启动。游戏识别、无障碍控制与七项自动化尚未实现，因此任务开关已禁用，避免误操作。").setPositiveButton("知道了",null).show());root.addView(info);
    setContentView(root);
  }
}
