/* Benben independent assistant V2 - no paid script code */
"auto";
var ROOT=files.join(files.getSdcardPath(),"BenbenIndependent");
files.ensureDir(ROOT+"/");
var LOG=ROOT+"/events.log",CFG=ROOT+"/config.json",STOP=ROOT+"/STOP";
var defaults={dryRun:true,cycleMs:60000,cooldownMs:900000,maxFailures:3,maxActions:12,enabled:{allianceHelp:true,dailyRewards:true},tasks:{allianceHelp:{steps:[["联盟"],["联盟互助","互助","帮助"],["一键帮助","全部帮助","帮助"]]},dailyRewards:{steps:[["任务","每日任务"],["一键领取","全部领取","领取"]]}}};
if(!files.exists(CFG))files.write(CFG,JSON.stringify(defaults,null,2));
var cfg=defaults;try{var c=JSON.parse(files.read(CFG));Object.keys(c).forEach(function(k){cfg[k]=c[k];});}catch(e){}
function log(s){var v=new Date().toISOString()+" "+s;console.log(v);files.append(LOG,v+"\n");}
if(!auto.service){log("Please enable accessibility");exit();}
var hist={};
function find(labels){for(var i=0;i<labels.length;i++){var x=text(labels[i]).findOnce()||desc(labels[i]).findOnce();if(x&&x.visibleToUser())return x;}return null;}
function tap(n){if(cfg.dryRun)return true;var p=n;for(var j=0;j<4&&p;j++,p=p.parent()){if(p.clickable()&&p.click())return true;}var b=n.bounds();return click(b.centerX(),b.centerY());}
while(!files.exists(STOP)){
 Object.keys(cfg.tasks||{}).forEach(function(key){if(!cfg.enabled[key])return;var h=hist[key]||{last:0,fails:0};hist[key]=h;if(h.fails>=cfg.maxFailures||Date.now()-h.last<cfg.cooldownMs)return;h.last=Date.now();var steps=cfg.tasks[key].steps||[],ok=true;for(var i=0;i<steps.length&&i<cfg.maxActions;i++){var n=find(steps[i]);if(!n||!tap(n)){ok=false;break;}sleep(900);}h.fails=ok?0:h.fails+1;log(key+" steps "+(ok?"completed":"failed")+" (not game-result verified)");if(!cfg.dryRun)back();});
 sleep(cfg.cycleMs);
}
