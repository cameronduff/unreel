#!/usr/bin/env python3
import subprocess
import time
import os

def run_adb(cmd):
    return subprocess.run(["adb"] + cmd.split(), capture_output=True, text=True)

print("Starting screen recording in background...")
rec_proc = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "15", "/sdcard/nav_test.mp4"])
time.sleep(1.0)

print("1. Tapping Direct Messages tab...")
run_adb("shell input tap 480 2200")
time.sleep(2.0)

print("2. Tapping conversation row in DM inbox...")
run_adb("shell input tap 500 600")
time.sleep(2.5)

print("3. Tapping Back button in chat...")
run_adb("shell input tap 60 90")
time.sleep(2.0)

print("4. Tapping Profile tab...")
run_adb("shell input tap 980 2200")
time.sleep(2.0)

print("5. Tapping Hamburger / Menu at top right...")
run_adb("shell input tap 940 90")
time.sleep(2.5)

print("6. Tapping Back button...")
run_adb("shell input tap 60 90")
time.sleep(1.5)

rec_proc.wait()
print("Screen recording finished. Pulling video...")
subprocess.run(["adb", "pull", "/sdcard/nav_test.mp4", "/home/cd_server/.gemini/antigravity-cli/brain/83a1a382-1ac9-4150-929c-e63c1cd41eac/nav_test.mp4"])
print("Done!")
