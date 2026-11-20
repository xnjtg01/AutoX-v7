package com.xnjtg.benben;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight persistent WindowManager overlay; screenshots are taken ONLY while
 * this overlay is removed from the window manager. No screen pixels are modified.
 */
public final class StatusOverlay {
  private final Activity activity;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final WindowManager manager;
  private WindowManager.LayoutParams params;
  private LinearLayout panel;
  private TextView state;
  private volatile boolean displayed=false;
  private volatile boolean wanted=false;
  private volatile boolean capturing=false;
  private String lastState="准备就绪";
  private Runnable stopAction;

  public StatusOverlay(Activity activity,Runnable stopAction) {
    this.activity=activity;
    this.manager=(WindowManager)activity.getSystemService(Context.WINDOW_SERVICE);
    this.stopAction=stopAction;
  }
  public void show(String message) {
    wanted=true;
    update(message);
    main.post(()->{
      if(panel==null)create();
      if(!displayed&&!capturing)attach();
    });
  }
  private void create() {
    panel=new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(13,9,13,9);
    panel.setBackgroundColor(Color.argb(225,18,29,48));
    state=new TextView(activity);
    state.setTextColor(Color.WHITE);
    state.setTextSize(12);
    state.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    state.setText(lastState);
    panel.addView(state);
    Button stop=new Button(activity);
    stop.setText("■ 停止采样");
    stop.setTextSize(11);
    stop.setAllCaps(false);
    stop.setOnClickListener(v->stopAction.run());
    panel.addView(stop,new LinearLayout.LayoutParams(195,90));
    int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
    params=new WindowManager.LayoutParams(215,WindowManager.LayoutParams.WRAP_CONTENT,type,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT);
    params.gravity=Gravity.TOP|Gravity.END;
    params.x=12;params.y=115;
  }
  private void attach() {
    if(!wanted||capturing||displayed)return;
    try {manager.addView(panel,params);displayed=true;}
    catch(Exception e){android.util.Log.e("BenBenOverlay","attach failed",e);}
  }
  public void update(String message) {
    lastState=message;
    main.post(()->{if(state!=null)state.setText(message);});
  }
  public void hide() {
    wanted=false;
    main.post(()->{if(displayed) {try{manager.removeViewImmediate(panel);}catch(Exception ignored){} displayed=false;}});
  }
  private void onMainBlocking(Runnable runnable) throws Exception {
    if(Looper.myLooper()==Looper.getMainLooper()){runnable.run();return;}
    CountDownLatch latch=new CountDownLatch(1);
    main.post(()->{try{runnable.run();}finally{latch.countDown();}});
    if(!latch.await(3,TimeUnit.SECONDS))throw new Exception("浮窗状态切换超时");
  }
  public void beforeScreenshot() throws Exception {
    capturing=true;
    onMainBlocking(()->{if(displayed){manager.removeViewImmediate(panel);displayed=false;}});
    // Window compositor has to present a frame after removing the overlay.
    Thread.sleep(180);
  }
  public void afterScreenshot() throws Exception {
    onMainBlocking(()->{capturing=false;if(wanted)attach();});
  }
}
