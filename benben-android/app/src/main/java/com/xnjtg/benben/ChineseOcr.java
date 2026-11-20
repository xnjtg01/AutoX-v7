package com.xnjtg.benben;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * On-device Chinese OCR, using bundled Google ML Kit model (no cloud/network).
 * No recognition output leaves the emulator unless user explicitly exports ZIP.
 */
public final class ChineseOcr implements AutoCloseable {
  public static final class Item {
    public final String text;
    public final Rect box;
    public Item(String text,Rect box){this.text=text;this.box=box;}
  }
  public static final class Result {
    public final String allText;
    public final List<Item> lines;
    public Result(String text,List<Item> lines){this.allText=text;this.lines=lines;}
    public String inArea(int x,int y,int w,int h){
      Rect target=new Rect(x,y,x+w,y+h);
      StringBuilder out=new StringBuilder();
      for(Item line:lines)if(line.box!=null&&Rect.intersects(line.box,target)){
        if(out.length()>0)out.append(' ');
        out.append(line.text);
      }
      return out.toString();
    }
    public boolean containsAny(String... terms){
      for(String term:terms)if(allText.contains(term))return true;
      return false;
    }
  }
  private final TextRecognizer recognizer=
    TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
  public Result read(Bitmap bitmap)throws Exception {
    Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),12,TimeUnit.SECONDS);
    ArrayList<Item> lines=new ArrayList<>();
    StringBuilder sb=new StringBuilder();
    for(Text.TextBlock block:text.getTextBlocks()){
      for(Text.Line line:block.getLines()){
        String value=line.getText();
        if(value==null||value.isEmpty())continue;
        Rect box=line.getBoundingBox();
        lines.add(new Item(value,box==null?null:new Rect(box)));
        if(sb.length()>0)sb.append(' ');
        sb.append(value);
      }
    }
    return new Result(sb.toString(),lines);
  }
  public static String compact(String text){
    if(text==null)return "";
    return text.replaceAll("\\s+","").replace('：',':');
  }
  @Override public void close(){recognizer.close();}
}
