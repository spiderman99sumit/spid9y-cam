import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { writeFile, mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

test('Native receiver remuxes real 1080p H.264 into decodable MPEG-TS and JPEG preview', {timeout:30000}, async () => {
  const children=[], sockets=new Set(); let receiver; let diagnostics='';
  const video=net.createServer(socket => {
    sockets.add(socket); socket.on('error',()=>{}); socket.on('close',()=>sockets.delete(socket));
    const generator=spawn('ffmpeg',['-hide_banner','-loglevel','error','-re','-f','lavfi','-i','testsrc2=size=1920x1080:rate=30','-t','10','-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-profile:v','baseline','-g','30','-f','h264','pipe:1'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
    children.push(generator); generator.stdout.pipe(socket); generator.stderr.on('data',d=>diagnostics+=d);socket.on('close',()=>generator.kill());
  });
  let exposureLocked=false,lastCommand={};
  const control=net.createServer(socket=>{sockets.add(socket);socket.on('error',()=>{});socket.on('close',()=>sockets.delete(socket));socket.once('data',d=>{const command=JSON.parse(d);if(Object.keys(command).length)lastCommand=command;if(typeof command.exposureLocked==='boolean')exposureLocked=command.exposureLocked;socket.end(JSON.stringify({running:true,connected:true,encodedFrames:30,exposureLocked})+'\n');});});
  await new Promise(resolve=>video.listen(0,'127.0.0.1',resolve));
  await new Promise(resolve=>control.listen(0,'127.0.0.1',resolve));
  const reservation=net.createServer();await new Promise(resolve=>reservation.listen(0,'127.0.0.1',resolve));const port=reservation.address().port;await new Promise(resolve=>reservation.close(resolve));
  const base=`http://127.0.0.1:${port}`;
  try {
    receiver=spawn(process.execPath,['native-server.mjs'],{windowsHide:true,env:{...process.env,NATIVE_PORT:String(port),NATIVE_VIDEO_PORT:String(video.address().port),NATIVE_CONTROL_PORT:String(control.address().port),NATIVE_RUNTIME_FILE:'tools/native-test-runtime.json'},stdio:['ignore','pipe','pipe']});
    const ready=new Promise((resolve,reject)=>{receiver.stdout.on('data',d=>{diagnostics+=d;if(d.toString().includes('Spid9y Cam:'))resolve();});receiver.on('error',reject);receiver.on('exit',code=>{if(code)reject(new Error(diagnostics));});});
    receiver.stderr.on('data',d=>diagnostics+=d);await ready;
    const blocked=await fetch(base+'/api/control',{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://external.invalid'},body:JSON.stringify({exposureLocked:true})});assert.equal(blocked.status,403);
    for(const route of ['/api/status','/preview.mjpg','/live.ts']) {
      assert.equal((await fetch(base+route,{headers:{Origin:'https://external.invalid'}})).status,403);
      assert.equal((await fetch(base+route,{headers:{'Sec-Fetch-Site':'cross-site'}})).status,403);
    }
    assert.equal((await fetch(base+'/api/status')).headers.get('access-control-allow-origin'),null);
    const locked=await fetch(base+'/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({exposureLocked:true})}).then(r=>r.json());assert.equal(locked.exposureLocked,true);
    const remote={cameraId:'1',focusLocked:true,zoom:1.5,exposureCompensation:2,torch:false,stabilization:true};
    await fetch(base+'/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...remote,unexpected:'discard'})});assert.deepEqual(lastCommand,remote);
    assert.equal((await fetch(base+'/native.js')).status,200);assert.equal((await fetch(base+'/native.css')).status,200);
    assert.equal((await fetch(base+'/api/webcam',{method:'POST',headers:{Origin:'https://external.invalid'},body:'{"action":"start"}'})).status,403);
    const previewTask=(async()=>{const response=await fetch(base+'/preview.mjpg',{signal:AbortSignal.timeout(15000)});const reader=response.body.getReader();let buffer=Buffer.alloc(0);try{while(buffer.indexOf(Buffer.from([255,217]))<0){const part=await reader.read();if(part.done)throw new Error('Preview ended');buffer=Buffer.concat([buffer,Buffer.from(part.value)]);}assert.ok(buffer.indexOf(Buffer.from([255,216]))>=0);}finally{await reader.cancel();}})();
    const response=await fetch(base+'/live.ts',{signal:AbortSignal.timeout(15000)});assert.equal(response.headers.get('content-type'),'video/mp2t');const reader=response.body.getReader();const pieces=[];let length=0;
    try{while(length<700000){const part=await reader.read();if(part.done)throw new Error('Video ended before enough data: '+diagnostics);pieces.push(Buffer.from(part.value));length+=part.value.length;}}finally{await reader.cancel();}
    await previewTask;
    const temp=await mkdtemp(path.join(tmpdir(),'pocketcam-verify-'));const file=path.join(temp,'native-stream.ts');await writeFile(file,Buffer.concat(pieces));
    const probe=spawn('ffmpeg',['-hide_banner','-loglevel','info','-i',file,'-t','1','-f','null','-'],{windowsHide:true,stdio:['ignore','ignore','pipe']});let probeOutput='';probe.stderr.on('data',d=>probeOutput+=d);const [code]=await once(probe,'close');assert.equal(code,0,probeOutput);assert.match(probeOutput,/1920x1080/);assert.match(probeOutput,/30 fps/);
  } finally {
    receiver?.kill();for(const child of children)child.kill();for(const socket of sockets)socket.destroy();
    await Promise.all([new Promise(resolve=>video.close(resolve)),new Promise(resolve=>control.close(resolve))]);
  }
});
