#!/usr/bin/env python3
import subprocess
import time

def run_adb(cmd):
    return subprocess.run(["adb"] + cmd.split(), capture_output=True, text=True)

print("Starting screen recording...")
rec_proc = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "15", "/sdcard/snappy_test.mp4"])
time.sleep(1.0)

# 1. Tap Direct tab
print("1. Tapping Direct Messages...")
run_adb("shell input tap 500 2200")
time.sleep(2.0)

# 2. Tap a conversation row (elisha zara kunalan-duff is at y=1050)
print("2. Tapping conversation row...")
run_adb("shell input tap 400 1050")
time.sleep(2.5)

# 3. Tap Back button in chat (top left 60, 150)
print("3. Tapping chat back button...")
run_adb("shell input tap 60 180")
time.sleep(2.0)

# 4. Tap Profile tab (bottom right 980, 2200)
print("4. Tapping Profile tab...")
run_adb("shell input tap 980 2200")
time.sleep(2.0)

# 5. Tap Hamburger / Settings menu (top right 950, 180)
print("5. Tapping Settings / Menu...")
run_adb("shell input tap 950 180")
time.sleep(2.5)

# 6. Tap back
print("6. Tapping back...")
run_adb("shell input keyevent 4")
time.sleep(1.5)

rec_proc.wait()
print("Recording finished. Pulling video...")
subprocess.run(["adb", "pull", "/sdcard/snappy_test.mp4", "/home/cd_server/.gemini/antigravity-cli/brain/83a1a382-1ac9-4150-929c-e63c1cd41eac/snappy_test.mp4"])
print("Video pulled successfully!")
