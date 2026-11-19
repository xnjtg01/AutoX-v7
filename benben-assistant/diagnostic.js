"auto";
auto.waitFor();
toast("奔奔王国助手 V1：诊断开始");
var result = [];
result.push("时间: " + new Date().toString());
result.push("设备: " + device.brand + " / " + device.model);
result.push("屏幕: " + device.width + "x" + device.height);
result.push("当前前台包名: " + currentPackage());
result.push("当前活动: " + currentActivity());
result.push("无障碍服务: 已启用");
try {
  var ok = requestScreenCapture(false);
  result.push("截图授权: " + (ok ? "成功" : "失败"));
  if (ok) {
    var shot = captureScreen();
    if (shot) {
      var dir = "/sdcard/BenBenAssistant";
      files.ensureDir(dir + "/");
      var path = dir + "/diagnostic.png";
      images.save(shot, path, "png", 100);
      result.push("截图保存: " + path);
      shot.recycle();
    } else {
      result.push("截图失败: 返回空图像");
    }
  }
} catch (e) {
  result.push("截图异常: " + String(e));
}
var report = result.join("\n");
log(report);
dialogs.alert("奔奔王国助手 - 诊断结果", report);
