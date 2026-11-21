package com.xnjtg.benben;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
public class GameAccessibilityService extends AccessibilityService {
 public static volatile boolean active=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private long lastAction=0;
 private int phase=0;
 private static final long COOLDOWN_MS=180000;
 private final Runnable tick=new Runnable(){@Override public void run(){try{step();}catch(Exception e){append("Error: "+e.getClass().getSimpleName());}handler.postDelayed(this,2000);}};
 @Override public void onServiceConnected(){active=true;prefs=getSharedPreferences("assistant",MODE_PRIVATE);append("服务已启用");handler.postDelayed(tick,1500);}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){}
 @Override public void onInterrupt(){append("服务已中断");}
 @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);super.onDestroy();}
 private void step(){if(prefs==null||!prefs.getBoolean("running",false))return;
 AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
 try{if(!"com.got.china".equals(String.valueOf(root.getPackageName())))return;
 if(System.currentTimeMillis()-lastAction<COOLDOWN_MS)return;
 if(prefs.getBoolean("help",true)){if(sequence(root,new String[][]{{"联盟"},{"联盟互助","互助","帮助"},{"一键帮助","全部帮助"}},"联盟互助"))return;}
 if(prefs.getBoolean("daily",false))sequence(root,new String[][]{{"任务","每日任务"},{"一键领取","全部领取"}},"每日奖励");
 }finally{root.recycle();}}
 private boolean sequence(AccessibilityNodeInfo root,String[][] labels,String task){
 String[] words=labels[Math.min(phase,labels.length-1)];
 AccessibilityNodeInfo found=null;
 for(String w:words){List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(w);for(AccessibilityNodeInfo n:nodes){if(n.isVisibleToUser()){found=n;break;}}if(found!=null)break;}
 if(found==null){phase=0;return false;}
 boolean observe=prefs.getBoolean("dry",true),done=observe||press(found);
 if(observe){append("观察："+task+" / "+words[0]+"（未点击）");lastAction=System.currentTimeMillis();phase=0;return true;}
 if(done){phase++;append("已尝试："+task+" 步骤 "+phase+"；结果尚未核实");if(phase>=labels.length){phase=0;lastAction=System.currentTimeMillis();}return true;}
 phase=0;lastAction=System.currentTimeMillis();append("点击失败："+task);return false;
 }
 private boolean press(AccessibilityNodeInfo node){AccessibilityNodeInfo p=node;while(p!=null){if(p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;p=p.getParent();}return false;}
 private void append(String msg){if(prefs==null)return;String old=prefs.getString("log","");String now=android.text.format.DateFormat.format("MM-dd HH:mm:ss",System.currentTimeMillis())+" "+msg+"\n";String joined=now+old;prefs.edit().putString("log",joined.substring(0,Math.min(9000,joined.length()))).apply();}
}
