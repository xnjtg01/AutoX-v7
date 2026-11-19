package com.xnjtg.benben;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
public class BenBenAccessibilityService extends AccessibilityService {
  public static volatile String lastPackage="尚未捕获";
  public static volatile String lastEvent="无";
  @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    if(event==null)return;
    CharSequence p=event.getPackageName();
    if(p!=null)lastPackage=p.toString();
    lastEvent=String.valueOf(event.getEventType());
  }
  @Override public void onInterrupt(){}
}