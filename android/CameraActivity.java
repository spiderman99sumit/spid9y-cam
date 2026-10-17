package io.github.spiderman99sumit.spid9ycam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.Range;
import android.util.Size;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class CameraActivity extends Activity {
  private CameraManager manager;
  private CameraCharacteristics caps;
  private CameraDevice camera;
  private CameraCaptureSession session;
  private CaptureRequest.Builder request;
  private MediaCodec encoder;
  private Surface encoderSurface, previewSurface;
  private HandlerThread cameraThread;
  private Handler handler;
  private SurfaceView preview;
  private LinearLayout controls;
  private TextView status;
  private Button startButton;
  private Spinner cameraPicker;
  private final ArrayList<String> cameraIds = new ArrayList<>();
  private String cameraId;
  private volatile boolean running = false, alive = true;
  private volatile Socket client;
  private ServerSocket videoServer, controlServer;
  private final ArrayBlockingQueue<byte[]> packets = new ArrayBlockingQueue<>(20);
  private volatile byte[] codecConfig;
  private volatile boolean needKeyFrame = true;
  private volatile long encodedFrames = 0, sentFrames = 0, droppedFrames = 0;
  private boolean aeLock = false, awbLock = false, focusLock = false, torch = false, stabilise = false;
  private float zoom = 1f;
  private int compensation = 0;
  private String lastError = "";
  private int generation = 0;
  private volatile long exposureNs = 0;
  private volatile int actualIso = 0, aeState = -1;
  private String codecAttempts = "";
  private final HashMap<String,Switch> controlSwitches = new HashMap<>();
  private boolean syncingControls = false;
  private final CameraCaptureSession.CaptureCallback captureStats = new CameraCaptureSession.CaptureCallback() {
    @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
      Long exposure=result.get(CaptureResult.SENSOR_EXPOSURE_TIME); Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY); Integer state=result.get(CaptureResult.CONTROL_AE_STATE);
      if(exposure!=null)exposureNs=exposure; if(iso!=null)actualIso=iso; if(state!=null)aeState=state;
    }
  };

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
    cameraThread = new HandlerThread("PocketCamera"); cameraThread.start(); handler = new Handler(cameraThread.getLooper());
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL); root.setBackgroundColor(0xff101612);
    preview = new SurfaceView(this); preview.getHolder().setFixedSize(1920,1080); root.addView(preview, new LinearLayout.LayoutParams(0, -1, 1));
    ScrollView scroll = new ScrollView(this); controls = new LinearLayout(this); controls.setOrientation(1); controls.setPadding(dp(15),dp(10),dp(15),dp(10)); scroll.addView(controls);
    root.addView(scroll, new LinearLayout.LayoutParams(dp(280), -1)); setContentView(root);
    label("SPID9Y CAM", 19); status = label("1080p / 30 fps / H.264\nCamera is stopped", 13);
    cameraPicker = new Spinner(this);
    try {
      ArrayList<String> names = new ArrayList<>();
      ArrayList<String> availableIds = new ArrayList<>(Arrays.asList(manager.getCameraIdList()));
      Collections.sort(availableIds, (a,b) -> {
        try {
          Integer fa=manager.getCameraCharacteristics(a).get(CameraCharacteristics.LENS_FACING);
          Integer fb=manager.getCameraCharacteristics(b).get(CameraCharacteristics.LENS_FACING);
          return Integer.compare(fa!=null && fa==CameraCharacteristics.LENS_FACING_FRONT ? 0 : 1,fb!=null && fb==CameraCharacteristics.LENS_FACING_FRONT ? 0 : 1);
        } catch(Exception ignored) { return 0; }
      });
      for (String id : availableIds) {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        boolean fullhd = false;
        if (map != null && map.getOutputSizes(MediaCodec.class) != null) for (Size size : map.getOutputSizes(MediaCodec.class)) if (size.getWidth() == 1920 && size.getHeight() == 1080) fullhd = true;
        if (!fullhd) continue;
        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        names.add((facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT ? "Front" : "Back") + " camera " + id);
        cameraIds.add(id);
      }
      cameraPicker.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names));
      if (!cameraIds.isEmpty()) { cameraId = cameraIds.get(0); caps = manager.getCameraCharacteristics(cameraId); }
    } catch (Exception e) { showError(e); }
    controls.addView(cameraPicker);
    startButton = new Button(this); startButton.setText("START 1080p CAMERA"); controls.addView(startButton);
    startButton.setOnClickListener(v -> { if (running) stopCamera(); else begin(); });
    Button reset = new Button(this); reset.setText("RESET CAMERA CONTROLS"); controls.addView(reset); reset.setOnClickListener(v -> { if (running) stopCamera(); aeLock=awbLock=focusLock=torch=stabilise=false; zoom=1; compensation=0; renderCameraControls(); });
    cameraPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(android.widget.AdapterView<?> p) {}
      public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) {
        if (running) return;
        try { cameraId = cameraIds.get(position); caps = manager.getCameraCharacteristics(cameraId); aeLock=awbLock=focusLock=torch=stabilise=false; compensation=0; zoom=1; renderCameraControls(); } catch(Exception e) { showError(e); }
      }
    });
    renderCameraControls();
    new Thread(this::serveVideo, "VideoSocket").start();
    new Thread(this::serveControl, "ControlSocket").start();
    handler.postDelayed(new Runnable() { public void run() { if (!alive) return; updateStatus(); handler.postDelayed(this,1000); } },1000);
  }
  private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
  private TextView label(String text,int size) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(0xffd5e6c7); t.setPadding(0,dp(6),0,dp(6)); controls.addView(t); return t; }
  private void toggle(String name, boolean enabled, boolean checked, java.util.function.Consumer<Boolean> change) {
    Switch s=new Switch(this); s.setText(name + (enabled ? "" : " (unsupported)")); s.setTextColor(0xffe0ecd5); s.setChecked(checked); s.setEnabled(enabled); controls.addView(s);
    controlSwitches.put(name,s);
    s.setOnCheckedChangeListener((button,value) -> { if(!syncingControls) { change.accept(value); applyControls(); } });
  }
  private boolean contains(int[] values,int n) { if(values!=null) for(int x:values) if(x==n) return true; return false; }
  private void renderCameraControls() {
    controlSwitches.clear();
    while(controls.getChildCount()>5) controls.removeViewAt(5);
    if(caps==null) { label("No camera exposes 1920 x 1080 video.",13); return; }
    toggle("Exposure lock", Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)), aeLock, value -> aeLock=value);
    toggle("White balance lock", Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)), awbLock, value -> awbLock=value);
    toggle("Focus lock", contains(caps.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CaptureRequest.CONTROL_AF_MODE_AUTO), focusLock, this::setFocusLock);
    toggle("Torch", Boolean.TRUE.equals(caps.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)), torch, value -> torch=value);
    boolean eis=contains(caps.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
    boolean ois=contains(caps.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
    toggle("Stabilization (may crop)",eis||ois,stabilise,value->stabilise=value);
    Range<Integer> range=caps.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    if(range!=null && range.getUpper()>range.getLower()) {
      TextView ev=label("Exposure compensation: 0 EV",12); SeekBar seek=new SeekBar(this); seek.setMax(range.getUpper()-range.getLower()); seek.setProgress(-range.getLower()); controls.addView(seek);
      seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} public void onProgressChanged(SeekBar b,int p,boolean user){ if(!user)return; compensation=p+range.getLower(); ev.setText(String.format(java.util.Locale.US,"Exposure compensation: %+.1f EV",compensation*caps.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue())); applyControls(); } });
    }
    Float maxZoom=caps.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    if(maxZoom!=null && maxZoom>1) {
      float maximum=Math.min(maxZoom,8f); TextView z=label("Zoom: 1.0x (digital zoom crops)",12); SeekBar seek=new SeekBar(this); seek.setMax(100); controls.addView(seek);
      seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} public void onProgressChanged(SeekBar b,int p,boolean user){ if(!user)return; zoom=1+(maximum-1)*p/100f; z.setText(String.format(java.util.Locale.US,"Zoom: %.1fx (digital crop)",zoom)); applyControls(); } });
    }
    label("Lock exposure after the picture settles. Focus lock focuses once, then holds.\n\nVideo: 1920 x 1080, target 30 fps, hardware H.264 at 12 Mbps. Keep this app in front.\n\nXiaomi HDR / Night / AI processing is not included. Digital zoom and stabilization crop the image.",11);
  }
  private int continuousFocus() { int[] modes=caps.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); return contains(modes,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : contains(modes,CaptureRequest.CONTROL_AF_MODE_AUTO) ? CaptureRequest.CONTROL_AF_MODE_AUTO : CaptureRequest.CONTROL_AF_MODE_OFF; }
  private void setFocusLock(boolean value) {
    if(focusLock==value)return; focusLock=value;
    if(running && request!=null) handler.post(() -> { try { if(session==null || request==null)return; request.set(CaptureRequest.CONTROL_AF_MODE,value ? CaptureRequest.CONTROL_AF_MODE_AUTO : continuousFocus()); request.set(CaptureRequest.CONTROL_AF_TRIGGER,value ? CaptureRequest.CONTROL_AF_TRIGGER_START : CaptureRequest.CONTROL_AF_TRIGGER_CANCEL); session.capture(request.build(),null,handler); request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE); } catch(Exception e) { showError(e); } });
  }
  private void syncSwitches() {
    syncingControls=true;
    String[] names={"Exposure lock","White balance lock","Focus lock","Torch","Stabilization (may crop)"};
    boolean[] values={aeLock,awbLock,focusLock,torch,stabilise};
    for(int i=0;i<names.length;i++)if(controlSwitches.get(names[i])!=null)controlSwitches.get(names[i]).setChecked(values[i]);
    syncingControls=false;
  }
  private void remoteCommand(JSONObject command) {
    if(command.length()==0)return;
    java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
    runOnUiThread(() -> {
      try {
        if(command.has("cameraId")) {
          String chosen=command.getString("cameraId"); int index=cameraIds.indexOf(chosen);
          if(index<0)throw new Exception("Camera does not expose 1080p.");
          if(!chosen.equals(cameraId)) {
            boolean restart=running; stopCamera(); cameraId=chosen; caps=manager.getCameraCharacteristics(chosen); aeLock=awbLock=focusLock=torch=stabilise=false; zoom=1; compensation=0;cameraPicker.setSelection(index);renderCameraControls();
            if(restart)handler.post(()->runOnUiThread(this::begin));
          }
        }
        if(command.has("exposureLocked") && Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)))aeLock=command.getBoolean("exposureLocked");
        if(command.has("whiteBalanceLocked") && Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)))awbLock=command.getBoolean("whiteBalanceLocked");
        if(command.has("focusLocked") && contains(caps.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CaptureRequest.CONTROL_AF_MODE_AUTO))setFocusLock(command.getBoolean("focusLocked"));
        if(command.has("torch") && Boolean.TRUE.equals(caps.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)))torch=command.getBoolean("torch");
        if(command.has("stabilization"))stabilise=command.getBoolean("stabilization");
        if(command.has("zoom")) { Float max=caps.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM); zoom=(float)Math.max(1,Math.min(max==null?1:max,command.getDouble("zoom"))); }
        if(command.has("exposureCompensation")) { Range<Integer> range=caps.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE); if(range!=null)compensation=Math.max(range.getLower(),Math.min(range.getUpper(),command.getInt("exposureCompensation"))); }
        syncSwitches(); applyControls();
        if(command.optBoolean("stop",false) && running)stopCamera();
        if(command.optBoolean("start",false) && !running)begin();
      } catch(Exception e) { showError(e); } finally { done.countDown(); }
    });
    try {done.await(1500,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
  }
  private void applyControls() { handler.post(() -> { if(request==null || session==null || !running) return; try { setControls(); session.setRepeatingRequest(request.build(),captureStats,handler); } catch(Exception e) { showError(e); } }); }
  private void setControls() {
    request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
    request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
    if(Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE))) request.set(CaptureRequest.CONTROL_AE_LOCK,aeLock);
    if(Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE))) request.set(CaptureRequest.CONTROL_AWB_LOCK,awbLock);
    request.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
    request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,compensation);
    request.set(CaptureRequest.CONTROL_AF_MODE,focusLock ? CaptureRequest.CONTROL_AF_MODE_AUTO : continuousFocus());
    request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
    if(Boolean.TRUE.equals(caps.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) request.set(CaptureRequest.FLASH_MODE,torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
    Range<Integer>[] ranges=caps.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES); Range<Integer> chosen=null;
    if(ranges!=null) for(Range<Integer> r:ranges) if(r.getUpper()==30 && (chosen==null || r.getLower()>chosen.getLower())) chosen=r;
    if(chosen!=null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,chosen);
    Rect active=caps.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    if(active!=null) { int w=(int)(active.width()/zoom),h=(int)(active.height()/zoom); request.set(CaptureRequest.SCALER_CROP_REGION,new Rect(active.centerX()-w/2,active.centerY()-h/2,active.centerX()+w/2,active.centerY()+h/2)); }
    boolean eis=contains(caps.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
    boolean ois=contains(caps.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
    if(eis) request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,stabilise ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    if(ois) request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,stabilise && !eis ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
  }
  private void begin() {
    if(running) return;
    if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.CAMERA},1); return; }
    if(!preview.getHolder().getSurface().isValid()) { lastError="Preview surface is loading. Tap Start again."; updateStatus(); return; }
    if(caps==null) return;
    cameraPicker.setEnabled(false); startButton.setText("STOP CAMERA"); running=true; int current=++generation;
    encodedFrames=sentFrames=droppedFrames=0; lastError=""; packets.clear(); codecConfig=null; needKeyFrame=true;
    handler.post(() -> {
      try {
        codecAttempts=""; encoder=null;
        for(MediaCodecInfo candidate:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
          if(!candidate.isEncoder() || (Build.VERSION.SDK_INT>=29 && !candidate.isHardwareAccelerated()))continue;
          boolean supports=false; for(String type:candidate.getSupportedTypes())if(type.equalsIgnoreCase("video/avc"))supports=true;
          if(!supports)continue;
          MediaCodec trial=null;
          try {
            MediaCodecInfo.CodecCapabilities capability=candidate.getCapabilitiesForType("video/avc");
            if(!capability.getVideoCapabilities().areSizeAndRateSupported(1920,1080,30))continue;
            boolean surface=false;for(int color:capability.colorFormats)if(color==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)surface=true;
            if(!surface)continue;
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",1920,1080);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,12000000); format.setInteger(MediaFormat.KEY_FRAME_RATE,30); format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            trial=MediaCodec.createByCodecName(candidate.getName()); trial.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder=trial; codecAttempts+=candidate.getName()+": configured; "; break;
          } catch(Exception e) { codecAttempts+=candidate.getName()+": "+e.toString()+"; ";if(trial!=null)try{trial.release();}catch(Exception ignored){} }
        }
        if(encoder==null)throw new Exception("No hardware encoder accepted 1080p30. "+codecAttempts);
        encoderSurface=encoder.createInputSurface(); encoder.start();
        previewSurface=preview.getHolder().getSurface();
        manager.openCamera(cameraId,new CameraDevice.StateCallback() {
          @Override public void onOpened(CameraDevice c) {
            if(!running || generation!=current) { c.close(); return; }
            camera=c;
            try {
              request=c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD); request.addTarget(encoderSurface); request.addTarget(previewSurface); setControls();
              c.createCaptureSession(Arrays.asList(encoderSurface,previewSurface),new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) { if(!running || generation!=current) { s.close(); return; } session=s; try { s.setRepeatingRequest(request.build(),captureStats,handler); if(focusLock) { request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START); s.capture(request.build(),null,handler); request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE); } drainEncoder(current); } catch(Exception e) { fail(e); } }
                @Override public void onConfigureFailed(CameraCaptureSession s) { fail(new Exception("This camera rejected 1080p preview + encoding.")); }
              },handler);
            } catch(Exception e) { fail(e); }
          }
          @Override public void onDisconnected(CameraDevice c) { c.close(); fail(new Exception("Camera disconnected")); }
          @Override public void onError(CameraDevice c,int code) { c.close(); fail(new Exception("Camera error "+code+". Close other camera apps.")); }
        },handler);
      } catch(Exception e) { fail(e); }
    });
  }
  private void drainEncoder(final int current) {
    if(!running || generation!=current || encoder==null) return;
    try {
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      for(int n=0;n<8;n++) {
        int index=encoder.dequeueOutputBuffer(info,0);
        if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { MediaFormat f=encoder.getOutputFormat(); ByteArrayOutputStream config=new ByteArrayOutputStream(); for(String name:new String[]{"csd-0","csd-1"}) { ByteBuffer b=f.getByteBuffer(name); if(b!=null) { byte[] data=new byte[b.remaining()]; b.get(data); config.write(data); } } codecConfig=config.toByteArray(); continue; }
        if(index<0) break;
        ByteBuffer b=encoder.getOutputBuffer(index);
        if(b!=null && info.size>0) {
          b.position(info.offset); b.limit(info.offset+info.size); byte[] data=new byte[info.size]; b.get(data);
          if((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0) codecConfig=data;
          else {
            encodedFrames++; boolean key=(info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0;
            if(client!=null && (!needKeyFrame || key)) {
              if(key && codecConfig!=null) { byte[] joined=new byte[codecConfig.length+data.length]; System.arraycopy(codecConfig,0,joined,0,codecConfig.length); System.arraycopy(data,0,joined,codecConfig.length,data.length); data=joined; }
              if(needKeyFrame && key) { packets.clear(); needKeyFrame=false; }
              if(!packets.offer(data)) { packets.clear(); needKeyFrame=true; droppedFrames++; requestKeyFrame(); }
            }
          }
        }
        encoder.releaseOutputBuffer(index,false);
      }
    } catch(Exception e) { fail(e); return; }
    handler.postDelayed(() -> drainEncoder(current),3);
  }
  private void requestKeyFrame() { if(encoder!=null) try { Bundle b=new Bundle(); b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0); encoder.setParameters(b); } catch(Exception ignored){} }
  private void serveVideo() {
    try {
      videoServer=new ServerSocket(); videoServer.bind(new InetSocketAddress("127.0.0.1",8876));
      while(alive) {
        Socket s=videoServer.accept(); s.setTcpNoDelay(true); s.setSendBufferSize(128*1024); client=s; packets.clear(); needKeyFrame=true; handler.post(this::requestKeyFrame);
        try {
          OutputStream output=s.getOutputStream();
          while(alive && !s.isClosed()) { byte[] packet=packets.poll(1000,java.util.concurrent.TimeUnit.MILLISECONDS); if(packet!=null) { output.write(packet); sentFrames++; } }
        } catch(Exception ignored) {} finally { if(client==s) client=null; try{s.close();}catch(Exception ignored){} }
      }
    } catch(Exception e) { if(alive) showError(e); }
  }
  private JSONObject info() {
    JSONObject o=new JSONObject(); try {
      o.put("running",running); o.put("connected",client!=null); o.put("width",1920); o.put("height",1080); o.put("targetFps",30); o.put("bitrate",12000000); o.put("encodedFrames",encodedFrames); o.put("sentFrames",sentFrames); o.put("droppedFrames",droppedFrames); o.put("error",lastError); o.put("cameraId",cameraId); o.put("exposureLocked",aeLock); o.put("focusLocked",focusLock); o.put("whiteBalanceLocked",awbLock); o.put("exposureNs",exposureNs); o.put("iso",actualIso); o.put("aeState",aeState); o.put("previewReady",preview.getHolder().getSurface().isValid()); o.put("version","0.1.0-beta.1"); o.put("codecAttempts",codecAttempts);
      if(caps!=null) { o.put("aeLockAvailable",Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE))); o.put("awbLockAvailable",Boolean.TRUE.equals(caps.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE))); o.put("hardwareLevel",caps.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)); o.put("sensorOrientation",caps.get(CameraCharacteristics.SENSOR_ORIENTATION)); o.put("encoder",encoder!=null ? encoder.getName() : ""); o.put("availableSceneModes",new JSONArray(caps.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)));
        o.put("focusLockAvailable",contains(caps.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CaptureRequest.CONTROL_AF_MODE_AUTO));o.put("torchAvailable",Boolean.TRUE.equals(caps.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)));o.put("stabilizationAvailable",contains(caps.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)||contains(caps.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON));
        o.put("zoom",zoom);o.put("maxZoom",caps.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));o.put("exposureCompensation",compensation);Range<Integer> ev=caps.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);o.put("evMin",ev==null?0:ev.getLower());o.put("evMax",ev==null?0:ev.getUpper());o.put("evStep",caps.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue());o.put("torch",torch);o.put("stabilization",stabilise);
        JSONArray cameras=new JSONArray();for(String id:cameraIds) { CameraCharacteristics c=manager.getCameraCharacteristics(id);JSONObject entry=new JSONObject();entry.put("id",id);entry.put("name",(c.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT?"Front":"Back")+" camera "+id);cameras.put(entry); }o.put("cameras",cameras);
      }
    } catch(Exception ignored){} return o;
  }
  private void serveControl() {
    try {
      controlServer=new ServerSocket(); controlServer.bind(new InetSocketAddress("127.0.0.1",8877));
      while(alive) {
        try(Socket s=controlServer.accept()) {
          s.setSoTimeout(2000); BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream())); String line=reader.readLine();
          if(line!=null) try { remoteCommand(new JSONObject(line)); } catch(Exception ignored){}
          s.getOutputStream().write((info().toString()+"\n").getBytes("UTF-8"));
        } catch(Exception ignored){}
      }
    } catch(Exception e) { if(alive) showError(e); }
  }
  private void updateStatus() { JSONObject data=info(); runOnUiThread(() -> status.setText((running ? "1080p / 30 fps target / 12 Mbps\n" : "Camera is stopped\n") + (client!=null ? "PC connected" : "Waiting for PC") + " | Frames "+encodedFrames+"\n"+(aeLock ? "EXPOSURE LOCKED" : "Auto exposure")+(lastError.isEmpty()?"":"\n"+lastError))); }
  private void fail(Exception e) { showError(e); runOnUiThread(this::stopCamera); }
  private void showError(Exception e) { lastError=e.toString(); android.util.Log.e("PocketCam",lastError,e); runOnUiThread(() -> { if(status!=null) status.setText(lastError); }); }
  private void stopCamera() {
    running=false; generation++; cameraPicker.setEnabled(true); startButton.setText("START 1080p CAMERA");
    handler.post(() -> {
      try{if(session!=null) { session.stopRepeating(); session.close(); }}catch(Exception ignored){} session=null; request=null;
      try{if(camera!=null) camera.close();}catch(Exception ignored){} camera=null;
      try{if(encoder!=null){encoder.stop();encoder.release();}}catch(Exception ignored){} encoder=null;
      if(encoderSurface!=null)encoderSurface.release(); encoderSurface=null; previewSurface=null;
      packets.clear(); codecConfig=null; needKeyFrame=true;
      try{if(client!=null)client.close();}catch(Exception ignored){} client=null; updateStatus();
    });
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) { super.onRequestPermissionsResult(requestCode,permissions,results); if(requestCode==1 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) begin(); else status.setText("Camera permission is needed. Tap Start and allow Camera."); }
  @Override public void onResume() { super.onResume(); handler.postDelayed(()->runOnUiThread(()->{if(alive && !running && !isFinishing() && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && preview.getHolder().getSurface().isValid())begin();}),500); }
  @Override public void onPause() { super.onPause(); if(running)stopCamera(); }
  @Override public void onDestroy() { alive=false; stopCamera(); try{if(videoServer!=null)videoServer.close();}catch(Exception ignored){} try{if(controlServer!=null)controlServer.close();}catch(Exception ignored){} handler.post(() -> cameraThread.quitSafely()); super.onDestroy(); }
}
