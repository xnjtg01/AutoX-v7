package com.xnjtg.benben;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Actual Activity is rendered on cloud Android, not mocked.
 * Catches the regression where the Spinner was constructed but NOT attached
 * to MainActivity's layout (found in 2026-11-21 cloud screenshot).
 */
@RunWith(AndroidJUnit4.class)
public final class ResourceSelectorInstrumentedTest {
  private static Spinner search(View v){
    if(v instanceof Spinner)return (Spinner)v;
    if(v instanceof ViewGroup){
      ViewGroup group=(ViewGroup)v;
      for(int i=0;i<group.getChildCount();i++){
        Spinner found=search(group.getChildAt(i));
        if(found!=null)return found;
      }
    }
    return null;
  }
  @Test public void resourceSpinnerIsVisibleAndAllowsChoosingWood(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(activity->{
        Spinner spinner=search(activity.getWindow().getDecorView());
        assertNotNull("Resource selector must be attached to layout",spinner);
        assertTrue("Resource selector has zero height",spinner.getHeight()>40);
        assertTrue("Resource selector must be visible",spinner.isShown());
        assertNotNull(spinner.getAdapter());
        assertTrue(spinner.getCount()>=4);
        spinner.setSelection(1);
        assertEquals("木材",String.valueOf(spinner.getSelectedItem()));
      });
    }
  }
}