"""Optional 1080p30 output using the user's installed OBS virtual camera."""
import json
import os
import pathlib
import queue
import shutil
import subprocess
import threading
import time

import numpy as np
import pyvirtualcam

ROOT = pathlib.Path(__file__).resolve().parent
WIDTH, HEIGHT, FPS = 1920, 1080, 30
FRAME_BYTES = WIDTH * HEIGHT * 3 // 2
frames = queue.Queue(maxsize=3)
stop = threading.Event()
metrics = {"decodedFrames": 0, "uniqueFramesSent": 0, "framesSent": 0, "lastFrameAt": 0, "error": "", "mirror": False}
decoder = None
ffmpeg = str(ROOT / 'tools' / 'ffmpeg.exe') if (ROOT / 'tools' / 'ffmpeg.exe').exists() else shutil.which('ffmpeg')

def mirror_setting():
    try:
        return json.loads((ROOT / 'webcam-settings.json').read_text(encoding='utf-8')).get('mirror') is True
    except (OSError, ValueError):
        return False


def read_exact(stream, size):
    data = bytearray(size)
    view = memoryview(data)
    offset = 0
    while offset < size and not stop.is_set():
        count = stream.readinto(view[offset:])
        if not count:
            return None
        offset += count
    return data if offset == size else None


def decode():
    global decoder
    while not stop.is_set():
        try:
            mirror = mirror_setting()
            metrics['mirror'] = mirror
            decoder = subprocess.Popen([
                ffmpeg, '-hide_banner', '-loglevel', 'error', '-fflags', 'nobuffer',
                '-flags', 'low_delay', '-probesize', '1048576', '-analyzeduration', '1000000',
                '-i', 'http://127.0.0.1:8780/live.ts', '-an', '-vf', 'hflip,setsar=1' if mirror else 'setsar=1',
                '-pix_fmt', 'nv12', '-f', 'rawvideo', 'pipe:1'
            ], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW,
                bufsize=FRAME_BYTES * 2)
            while not stop.is_set():
                frame = read_exact(decoder.stdout, FRAME_BYTES)
                if frame is None:
                    break
                item = (frame, time.monotonic())
                metrics['decodedFrames'] += 1
                metrics['error'] = ''
                metrics['lastFrameAt'] = int(time.time() * 1000)
                if frames.full():
                    try:
                        frames.get_nowait()
                    except queue.Empty:
                        pass
                frames.put_nowait(item)
        except Exception as error:
            metrics['error'] = str(error)
        finally:
            if decoder:
                decoder.terminate()
                try:
                    decoder.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    decoder.kill()
                if decoder.stdout:
                    decoder.stdout.close()
        stop.wait(1)


def main():
    black = np.empty((HEIGHT * 3 // 2, WIDTH), dtype=np.uint8)
    black[:HEIGHT] = 16
    black[HEIGHT:] = 128
    last_frame, last_time = black, 0
    start = time.monotonic()
    last_save = 0
    requested_mirror = mirror_setting()
    try:
        with pyvirtualcam.Camera(WIDTH, HEIGHT, FPS, fmt=pyvirtualcam.PixelFormat.NV12, backend='obs') as camera:
            print(f'Webcam ready: {camera.device} | 1920x1080 | 16:9 | 30 fps', flush=True)
            threading.Thread(target=decode, name='H264 decoder', daemon=True).start()
            while not stop.is_set():
                fresh = False
                try:
                    raw, last_time = frames.get(timeout=1 / FPS)
                    last_frame = np.frombuffer(raw, dtype=np.uint8).reshape(HEIGHT * 3 // 2, WIDTH)
                    fresh = True
                except queue.Empty:
                    pass
                camera.send(last_frame if time.monotonic() - last_time < 2 else black)
                metrics['framesSent'] += 1
                if fresh:
                    metrics['uniqueFramesSent'] += 1
                now = time.monotonic()
                if now - last_save >= 1:
                    desired = mirror_setting()
                    if desired != requested_mirror:
                        requested_mirror = desired
                        if decoder:
                            decoder.terminate()
                        while not frames.empty():
                            try:
                                frames.get_nowait()
                            except queue.Empty:
                                break
                        last_time = 0
                    state = {**metrics, 'pid': os.getpid(), 'decoderPid': decoder.pid if decoder else None,
                             'device': camera.device, 'width': WIDTH, 'height': HEIGHT, 'targetFps': FPS,
                             'elapsed': now - start, 'running': True, 'updatedAt': int(time.time() * 1000)}
                    temp = ROOT / 'webcam-runtime.tmp'
                    temp.write_text(json.dumps(state), encoding='utf-8')
                    temp.replace(ROOT / 'webcam-runtime.json')
                    last_save = now
                camera.sleep_until_next_frame()
    finally:
        stop.set()
        if decoder:
            decoder.terminate()


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        pass
