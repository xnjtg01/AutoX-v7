package com.xnjtg.benben;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;
public class MainActivity extends Activity {
 private TextView state,log; private SharedPreferences prefs;
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("assistant",MODE_PRIVATE);
 LinearLayout base=new LinearLayout(this);base.setOrientation(1);base.setPadding(28,24,28,24);base.setBackgroundColor(Color.WHITE);
 ScrollView scroll=new ScrollView(this);scroll.addView(base);setContentView(scroll);
 TextView heading=new TextView(this);heading.setText("奔奔独立助手 · OCR 引擎测试版");heading.setTextSize(22);heading.setTextColor(Color.rgb(15,50,70));base.addView(heading);
 TextView warning=new TextView(this);warning.setText("目前仅支持文本控件实验：联盟互助、每日奖励。采集、打野、集结尚未实现。默认安全观察模式，不执行点击。");warning.setPadding(0,16,0,16);base.addView(warning);
 state=new TextView(this);base.addView(state);
 Button access=new Button(this);access.setText("1. 打开无障碍设置");base.addView(access);access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
 switcher(base,"安全观察模式（不点击）","dry",true);
 switcher(base,"联盟互助（实验功能）","help",true);
 switcher(base,"每日任务奖励（实验功能）","daily",false);
 Button ocr=new Button(this);ocr.setText("OCR 识别当前游戏画面（测试）");base.addView(ocr);ocr.setOnClickListener(v->{prefs.edit().putBoolean("ocr_request",true).apply();Intent game=getPackageManager().getLaunchIntentForPackage("com.got.china");if(game!=null)startActivity(game);else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));});
        Button start=new Button(this);start.setText("2. 开始执行");base.addView(start);start.setOnClickListener(v->{prefs.edit().putBoolean("running",true).apply();refresh();});
 Button stop=new Button(this);stop.setText("停止所有任务");base.addView(stop);stop.setOnClickListener(v->{prefs.edit().putBoolean("running",false).apply();refresh();});
 Button update=new Button(this);update.setText("刷新日志");base.addView(update);update.setOnClickListener(v->refresh());
 log=new TextView(this);log.setTextSize(12);log.setTextIsSelectable(true);base.addView(log);refresh();
 }
 private void switcher(LinearLayout root,String title,String key,boolean def){Switch s=new Switch(this);s.setText(title);s.setChecked(prefs.getBoolean(key,def));root.addView(s);s.setOnCheckedChangeListener((view,checked)->prefs.edit().putBoolean(key,checked).apply());}
 @Override protected void onResume(){super.onResume();refresh();}
 private void refresh(){if(state==null)return;state.setText("执行状态："+(prefs.getBoolean("running",false)?"请求运行中":"已停止")+"\n服务状态："+(GameAccessibilityService.active?"已连接":"未连接"));if(log!=null)log.setText(prefs.getString("log","尚无执行记录"));}
}
