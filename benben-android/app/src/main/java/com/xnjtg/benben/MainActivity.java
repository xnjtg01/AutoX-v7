package com.xnjtg.benben;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
  private TextView status;
  private ImageView preview;
  private Button tapTarget;
  private void show(String message){runOnUiThread(()->status.setText(message));}
  private void async(Runnable runnable){new Thread(runnable,"benben-diagnostics").start();}
  private Button button(LinearLayout root,String label,View.OnClickListener fn){
    Button b=new Button(this);b.setText(label);b.setOnClickListener(fn);root.addView(b);return b;
  }
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root = new LinearLayout(this);root.setOrientation(1);root.setPadding(26,18,26,24);root.setBackgroundColor(Color.rgb(247,249,252));
    scroll.addView(root);
    TextView title=new TextView(this);title.setText("奔奔王国助手");title.setTextSize(24);title.setTextColor(Color.rgb(18,44,72));root.addView(title);
    TextView subtitle=new TextView(this);subtitle.setText("MuMu 独立 APK · V0.3 Root 识别诊断");root.addView(subtitle);
    status=new TextView(this);status.setText("检测模式：不会自动操作游戏。");status.setTextIsSelectable(true);status.setPadding(0,16,0,14);root.addView(status);
    button(root,"① 检测 Root 权限",v->{
      show("检测 Root 中……");
      async(()->{try{show("Root 可用："+RootBridge.checkRoot());}catch(Exception e){show("Root 不可用："+e.getMessage());}});
    });
    button(root,"② 5 秒后截图（切换到游戏）",v->{
      show("5 秒倒计时，请切换至游戏主界面……");
      async(()->{
        try {
          Thread.sleep(5000);
          File file=new File(getExternalFilesDir(null),"benben-screen.png");
          Bitmap b=RootBridge.screenshot(file);
          String verdict=RootBridge.classify(b);
          show("截图成功："+b.getWidth()+"×"+b.getHeight()+"\n"+verdict+"\n文件："+file.getAbsolutePath());
          runOnUiThread(()->preview.setImageBitmap(b));
        }catch(Exception e){show("截图失败："+e.getMessage());}
      });
    });
    tapTarget=button(root,"③ 点这里做 Root 点击测试（仅本页面）",v->
      Toast.makeText(this,"点击目标收到事件",Toast.LENGTH_SHORT).show());
    button(root,"运行一次安全点击测试",v->{
      int[] pos=new int[2];tapTarget.getLocationOnScreen(pos);
      int x=pos[0]+tapTarget.getWidth()/2,y=pos[1]+tapTarget.getHeight()/2;
      if(tapTarget.getWidth()==0){show("目标尚未布局完成");return;}
      show("正在使用 Root 点击本应用内测试按钮……");
      async(()->{try{show(RootBridge.tap(x,y)+"，坐标："+x+","+y);}catch(Exception e){show("Root 点击失败："+e.getMessage());}});
    });
    button(root,"开启无障碍服务（备用）",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    button(root,"查看无障碍记录",v->show("最近前台包名："+BenBenAccessibilityService.lastPackage+"\n事件类型："+BenBenAccessibilityService.lastEvent));
    TextView section=new TextView(this);section.setText("\n以下任务开发中，暂不允许自动运行：");root.addView(section);
    String[] tasks={"自动领取奖励","资源采集","自动打野怪","训练士兵","加入集结","每日任务","联盟帮助"};
    for(String task:tasks){Switch s=new Switch(this);s.setText(task);s.setEnabled(false);root.addView(s);}
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(360);root.addView(preview);
    setContentView(scroll);
  }
}
