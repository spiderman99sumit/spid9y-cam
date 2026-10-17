const $=id=>document.getElementById(id);let camera={},webcam={},busy=false,cameraList='',samples=[],webSamples=[],pendingMirror=null;
const toggles=[['ae','aeLockAvailable','exposureLocked'],['awb','awbLockAvailable','whiteBalanceLocked'],['focus','focusLockAvailable','focusLocked'],['torch','torchAvailable','torch'],['stabilization','stabilizationAvailable','stabilization']];
function rate(list,count){const time=performance.now();if(list.length&&count<list.at(-1)[1])list.length=0;list.push([time,count||0]);if(list.length>7)list.shift();return list.length>1?(list.at(-1)[1]-list[0][1])*1000/(time-list[0][0]):0;}
async function update(){try{
 const s=await fetch('/api/status').then(r=>r.json());camera=s.camera;webcam=s.webcam||{};$('url').value=s.sourceUrl;const online=!!camera.version,fresh=Date.now()-s.receiver.lastDataAt<3000;
 $('status').textContent=camera.running?(fresh?'Camera live · 1080p / 16:9':'Camera started · waiting for video'):(camera.error||'Open Spid9y Cam on your phone.');
 const fps=rate(samples,camera.encodedFrames);$('metrics').textContent=camera.running?`1920 × 1080 · ${fps.toFixed(0)} encoder fps · ${camera.encoder||'H.264'} · ${camera.droppedFrames||0} transport queue drops`:'Camera is stopped';
 const cameras=camera.cameras||[],signature=JSON.stringify(cameras);if(signature!==cameraList){$('camera').replaceChildren(...cameras.map(c=>new Option(c.name,c.id)));cameraList=signature;}if(document.activeElement!==$('camera'))$('camera').value=camera.cameraId||'';$('camera').disabled=busy||!cameras.length;
 $('start').disabled=busy||!online||camera.running;$('stop').disabled=busy||!camera.running;
 for(const[id,available,value]of toggles){$(id).checked=!!camera[value];$(id).disabled=busy||!online||!camera[available];}
 $('zoom').max=Math.min(camera.maxZoom||1,8);$('zoom').disabled=busy||!online||Number($('zoom').max)<=1;if(document.activeElement!==$('zoom'))$('zoom').value=camera.zoom||1;$('zoom-value').textContent=Number($('zoom').value).toFixed(1)+'×';
 $('ev').min=camera.evMin||0;$('ev').max=camera.evMax||0;$('ev').disabled=busy||!online||camera.evMin===camera.evMax;if(document.activeElement!==$('ev'))$('ev').value=camera.exposureCompensation||0;$('ev-value').textContent=(Number($('ev').value)*(camera.evStep||0)).toFixed(1)+' EV';
 $('webcam').disabled=busy;$('webcam').textContent=webcam.running?'Stop webcam output':'Start webcam output';const webcamFps=rate(webSamples,webcam.uniqueFramesSent);$('webcam-metrics').textContent=webcam.running?`OBS Virtual Camera · 1080p · ${webcamFps.toFixed(0)} fresh fps`:'Webcam output is off';
 if(pendingMirror && (webcam.mirror===pendingMirror.value || Date.now()>pendingMirror.deadline))pendingMirror=null;
 $('mirror').checked=pendingMirror?pendingMirror.value:!!webcam.mirror;$('mirror').disabled=busy||!!pendingMirror||!webcam.running||!webcam.supportsMirror;$('preview').style.transform=webcam.running&&webcam.mirror?'scaleX(-1)':'';$('preview-label').textContent='MONITOR · 540p / 30 fps'+(webcam.running&&webcam.mirror?' · MIRRORED':'');
}catch(e){$('status').textContent='PC receiver unavailable. Run Spid9y Cam - Start.';}}
async function command(endpoint,value){busy=true;try{const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});const result=await response.json();if(!response.ok)throw new Error(result.error);$('notice').textContent='Applied.';}catch(e){$('notice').textContent=e.message;}finally{busy=false;await update();}}
for(const[id,,key]of toggles)$(id).onchange=()=>command('/api/control',{[key]:$(id).checked});
$('camera').onchange=()=>command('/api/control',{cameraId:$('camera').value});$('start').onclick=()=>command('/api/control',{start:true});$('stop').onclick=()=>command('/api/control',{stop:true});
$('zoom').oninput=()=>$('zoom-value').textContent=Number($('zoom').value).toFixed(1)+'×';$('zoom').onchange=()=>command('/api/control',{zoom:Number($('zoom').value)});
$('ev').oninput=()=>$('ev-value').textContent=(Number($('ev').value)*(camera.evStep||0)).toFixed(1)+' EV';$('ev').onchange=()=>command('/api/control',{exposureCompensation:Number($('ev').value)});
$('webcam').onclick=()=>command('/api/webcam',{action:webcam.running?'stop':'start'});$('mirror').onchange=()=>{pendingMirror={value:$('mirror').checked,deadline:Date.now()+10000};command('/api/webcam',{mirror:pendingMirror.value});};
$('copy').onclick=async()=>{try{await navigator.clipboard.writeText($('url').value);$('copy').textContent='Copied';}catch{$('url').select();}};
$('preview').onerror=()=>setTimeout(()=>{$('preview').src='/preview.mjpg?t='+Date.now();},2000);update();setInterval(update,1000);
