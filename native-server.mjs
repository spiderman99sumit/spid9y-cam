import http from 'node:http';
import net from 'node:net';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const bundled = path.join(root,'tools','ffmpeg.exe');
const ffmpeg = process.env.FFMPEG_PATH || (existsSync(bundled) ? bundled : 'ffmpeg');
const port = Number(process.env.NATIVE_PORT || 8780);
const videoPort = Number(process.env.NATIVE_VIDEO_PORT || 8876);
const controlPort = Number(process.env.NATIVE_CONTROL_PORT || 8877);
const transport = new Set(), previews = new Set();
let child = null, stopped = false, retry, lastError = '', bytes = 0, chunks = 0, previewFrames = 0, jpegBuffer = Buffer.alloc(0), lastJpeg = null, lastDataAt = 0;
let cameraStatus = { running: false, connected: false, error: 'Waiting for native camera app' };
const supportsMirror = /\bhflip\b/.test(spawnSync(ffmpeg,['-hide_banner','-filters'],{windowsHide:true,encoding:'utf8'}).stdout || '');
async function webcamStatus() {
  try { const state=JSON.parse(await readFile(path.join(root,'webcam-runtime.json'),'utf8'));return {...state,running:Date.now()-(state.updatedAt || 0)<3500,supportsMirror}; }
  catch {return {running:false,mirror:false,supportsMirror};}
}

function phoneCommand(command = {}) {
  return new Promise((resolve,reject) => {
    const socket = net.connect({host:'127.0.0.1',port:controlPort}); let input='';
    socket.setTimeout(2000);
    socket.on('connect',() => socket.write(JSON.stringify(command)+'\n'));
    socket.on('data',data => { input+=data; if(input.includes('\n')) { try { resolve(JSON.parse(input.split('\n')[0])); } catch(e) { reject(e); } socket.destroy(); } });
    socket.on('error',reject); socket.on('timeout',() => { socket.destroy(); reject(new Error('Phone control timed out')); });
    socket.on('end',() => { if(!input.includes('\n'))reject(new Error('Phone app is not open. Run Start Native Camera and keep it on screen.')); });
  });
}
function sendFrame(jpeg) {
  lastJpeg = jpeg;
  const header = Buffer.from(`--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.length}\r\n\r\n`);
  for(const res of previews) if(res.writableLength < 256*1024) { res.write(header); res.write(jpeg); res.write('\r\n'); }
}
function parseJpegs(data) {
  jpegBuffer=Buffer.concat([jpegBuffer,data]);
  while(true) {
    const start=jpegBuffer.indexOf(Buffer.from([255,216]));
    if(start<0) { jpegBuffer=Buffer.alloc(0); break; }
    const end=jpegBuffer.indexOf(Buffer.from([255,217]),start+2);
    if(end<0) { if(start>0)jpegBuffer=jpegBuffer.subarray(start); break; }
    previewFrames++; sendFrame(jpegBuffer.subarray(start,end+2)); jpegBuffer=jpegBuffer.subarray(end+2);
  }
  if(jpegBuffer.length>2*1024*1024)jpegBuffer=Buffer.alloc(0);
}
function receive() {
  if(stopped || child) return;
  const args=['-hide_banner','-loglevel','warning','-fflags','+genpts+nobuffer','-flags','low_delay','-probesize','32768','-analyzeduration','0','-r','30','-f','h264','-i',`tcp://127.0.0.1:${videoPort}`,
    '-map','0:v:0','-c:v','copy','-bsf:v','h264_metadata=sample_aspect_ratio=1/1','-an','-mpegts_flags','resend_headers','-muxdelay','0','-flush_packets','1','-f','mpegts','pipe:1',
    '-map','0:v:0','-vf','fps=30,scale=960:540:force_original_aspect_ratio=decrease','-q:v','5','-f','image2pipe','-c:v','mjpeg','pipe:3'];
  const processHandle=spawn(ffmpeg,args,{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe','pipe']}); child=processHandle;
  processHandle.stdout.on('data',data => {
    bytes+=data.length; chunks++; lastDataAt=Date.now();
    for(const res of transport) { if(res.writableLength>2*1024*1024) { res.destroy(); transport.delete(res); } else res.write(data); }
  });
  processHandle.stdio[3].on('data',parseJpegs);
  processHandle.stderr.on('data',data => { const message=data.toString().trim(); if(message) { lastError=message.slice(-700); if(!message.includes('non-existing PPS') && !message.includes('decode_slice_header')) console.log(message); } });
  processHandle.on('error',e => { lastError=e.message; });
  processHandle.on('close',() => {
    if(child===processHandle) child=null;
    jpegBuffer=Buffer.alloc(0); lastJpeg=null;
    for(const res of transport)res.end(); transport.clear();
    if(!stopped)retry=setTimeout(receive,2000);
  });
}
const server=http.createServer((req,res) => {
  handleRequest(req,res).catch(error => {
    console.error('Request failed:',error.message);
    if(!res.headersSent)res.writeHead(500,{'Content-Type':'application/json'});
    if(!res.destroyed)res.end(JSON.stringify({error:'Request could not be completed'}));
  });
});
async function handleRequest(req,res) {
  const host=(req.headers.host || '').replace(/:\d+$/,'');
  if(!['localhost','127.0.0.1'].includes(host)) { res.writeHead(403).end(); return; }
  const url=new URL(req.url,'http://127.0.0.1');
  res.setHeader('Cache-Control','no-store'); res.setHeader('X-Content-Type-Options','nosniff');
  res.setHeader('Referrer-Policy','no-referrer');
  const sensitive=url.pathname.startsWith('/api/')||['/live.ts','/preview.mjpg'].includes(url.pathname);
  const origins=[`http://localhost:${port}`,`http://127.0.0.1:${port}`];
  if(sensitive && ((req.headers.origin && !origins.includes(req.headers.origin)) || req.headers['sec-fetch-site']==='cross-site')) {res.writeHead(403).end('Local camera access only');return;}
  if(url.pathname==='/native.css'||url.pathname==='/native.js') {
    try {res.setHeader('Content-Type',url.pathname.endsWith('.js')?'text/javascript':'text/css');res.end(await readFile(path.join(root,'public',url.pathname.slice(1))));}catch{res.writeHead(404).end();}return;
  }
  if(url.pathname==='/api/status') {
    res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({app:{id:'spid9y-cam',version:'0.1.0-beta.1'},camera:cameraStatus,webcam:await webcamStatus(),receiver:{running:!!child,bytes,chunks,previewFrames,lastDataAt,error:lastError},sourceUrl:`http://127.0.0.1:${port}/live.ts`})); return;
  }
  if(url.pathname==='/api/control' && req.method==='POST') {
    if(req.headers.origin && ![`http://localhost:${port}`,`http://127.0.0.1:${port}`].includes(req.headers.origin)) { res.writeHead(403).end(); return; }
    let body=''; for await(const data of req) { body+=data; if(body.length>1024) { res.writeHead(413).end(); return; } }
    try { const command=JSON.parse(body); const allowed={}; for(const key of ['exposureLocked','whiteBalanceLocked','focusLocked','torch','stabilization','start','stop']) if(typeof command[key]==='boolean')allowed[key]=command[key]; for(const key of ['zoom','exposureCompensation']) if(typeof command[key]==='number' && Number.isFinite(command[key]))allowed[key]=command[key];if(typeof command.cameraId==='string' && command.cameraId.length<40)allowed.cameraId=command.cameraId; cameraStatus=await phoneCommand(allowed); res.setHeader('Content-Type','application/json');res.end(JSON.stringify(cameraStatus)); }
    catch(e) { res.writeHead(503,{'Content-Type':'application/json'}).end(JSON.stringify({error:e.message})); } return;
  }
  if(url.pathname==='/api/webcam' && req.method==='POST') {
    if(req.headers.origin && ![`http://localhost:${port}`,`http://127.0.0.1:${port}`].includes(req.headers.origin)) {res.writeHead(403).end();return;}
    let body='';for await(const data of req){body+=data;if(body.length>1024){res.writeHead(413).end();return;}}
    try {
      const command=JSON.parse(body);
      if(typeof command.mirror==='boolean') {if(!supportsMirror)throw new Error('Native hflip is unavailable in this FFmpeg build.');await writeFile(path.join(root,'webcam-settings.json'),JSON.stringify({mirror:command.mirror}));}
      if(command.action==='start' || command.action==='stop') {
        const script=path.join(root,command.action==='start'?'Start-Webcam.ps1':'Stop-Webcam.ps1');
        if(!existsSync(script))throw new Error('Webcam scripts are not installed.');
        await new Promise((resolve,reject)=>{const helper=spawn('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',script],{windowsHide:true,stdio:['ignore','ignore','pipe']});let errors='';helper.stderr.on('data',d=>errors+=d);helper.on('error',reject);helper.on('exit',code=>{helper.stderr.destroy();code?reject(new Error(errors||'Webcam helper failed')):resolve();});});
      }
      res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ok:true,...await webcamStatus()}));
    }catch(e){res.writeHead(400,{'Content-Type':'application/json'}).end(JSON.stringify({error:e.message}));}return;
  }
  if(url.pathname==='/live.ts') {
    res.writeHead(200,{'Content-Type':'video/mp2t','Connection':'keep-alive'}); res.flushHeaders(); transport.add(res); req.on('close',()=>transport.delete(res)); return;
  }
  if(url.pathname==='/preview.mjpg') {
    res.writeHead(200,{'Content-Type':'multipart/x-mixed-replace; boundary=frame','Connection':'keep-alive'}); res.flushHeaders(); previews.add(res); if(lastJpeg)sendFrame(lastJpeg); req.on('close',()=>previews.delete(res));return;
  }
  if(url.pathname==='/') {
    try { res.setHeader('Content-Type','text/html');res.end(await readFile(path.join(root,'public','native.html'))); } catch {res.writeHead(500).end();}return;
  }
  res.writeHead(404).end();
}
const statusTimer=setInterval(async() => { try {cameraStatus=await phoneCommand();} catch(e) {cameraStatus={running:false,connected:false,error:e.message};} },1000);
server.on('error',e=>{console.error(e);process.exit(1);});
server.listen(port,'127.0.0.1',async()=>{const runtimeFile=path.resolve(root,process.env.NATIVE_RUNTIME_FILE || 'native-runtime.json');await mkdir(path.dirname(runtimeFile),{recursive:true});await writeFile(runtimeFile,JSON.stringify({pid:process.pid,port}));console.log(`Spid9y Cam: http://localhost:${port}\nOBS Media Source: http://127.0.0.1:${port}/live.ts`);receive();});
function close() {stopped=true;clearInterval(statusTimer);clearTimeout(retry);child?.kill();for(const res of transport)res.destroy();for(const res of previews)res.destroy();server.close(()=>process.exit(0));}
process.on('SIGINT',close);process.on('SIGTERM',close);
