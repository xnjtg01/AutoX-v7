"auto";
auto.waitFor();
// 奔奔王国国内服：只读画面识别 V1
if (!requestScreenCapture(false)) { dialogs.alert("失败", "未授权截图"); exit(); }
function darkAt(im, px, py) {
  var x = Math.min(im.getWidth()-1,Math.round(px/924*im.getWidth()));
  var y = Math.min(im.getHeight()-1,Math.round(py/2048*im.getHeight()));
  var c = images.pixel(im,x,y);
  return (colors.red(c)+colors.green(c)+colors.blue(c))/3 < 110;
}
var im=captureScreen();
if(!im){dialogs.alert("失败","截图为空");exit();}
var a=[[100,800],[250,800],[450,1000],[100,1100],[30,900]], hits=0;
for(var i=0;i<a.length;i++) if(darkAt(im,a[i][0],a[i][1])) hits++;
var state=hits>=4?"城镇-行军队列已展开":"城镇-行军队列未展开或其他页面";
var path="/sdcard/BenBenAssistant/recognition-latest.png";
files.ensureDir("/sdcard/BenBenAssistant/");
images.save(im,path,"png",90);
var msg="识别："+state+"\n特征命中："+hits+"/5\n屏幕："+im.getWidth()+"x"+im.getHeight()+"\n截图："+path+"\n仅检测面板，不自动点击，不统计队伍。";
im.recycle(); log(msg); dialogs.alert("奔奔王国识别 V1",msg);
