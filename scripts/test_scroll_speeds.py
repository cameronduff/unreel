#!/usr/bin/env python3
import subprocess
import time
import sys
import re

ADB = "adb"

def run_adb(cmd):
    full_cmd = f"{ADB} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def clear_logcat():
    subprocess.run(f"{ADB} logcat -c", shell=True)

def get_logs():
    res = subprocess.run(f"{ADB} logcat -d -v time", shell=True, capture_output=True, text=True)
    return res.stdout

def test_scroll(speed_name, start_y, end_y, duration_ms, count=3):
    print(f"\n--- Testing Scroll Speed: {speed_name} (duration={duration_ms}ms, swipes={count}) ---")
    clear_logcat()
    
    start_time = time.time()
    for i in range(count):
        # Swipe up (scrolls content down)
        run_adb(f"shell input swipe 540 {start_y} 540 {end_y} {duration_ms}")
        time.sleep(0.3)
    
    elapsed = time.time() - start_time
    time.sleep(0.5) # Wait for remaining events
    
    logs = get_logs()
    
    # Analyze logs
    unreel_lines = [l for l in logs.splitlines() if any(k in l for k in ["UnreelService", "TouchAbsorber", "UnreelClips", "UnreelNav", "UnreelModal"])]
    detach_calls = [l for l in unreel_lines if "DETACH" in l or "detachOverlay" in l or "reelsBounds=null" in l]
    attach_calls = [l for l in unreel_lines if "ATTACH" in l or "attachOverlay" in l]
    reels_detected = [l for l in unreel_lines if "REELS DETECTED" in l]
    modal_matches = [l for l in unreel_lines if "isModalOpen=true" in l or "Matched modal" in l]
    splash_matches = [l for l in unreel_lines if "isSplash=true" in l]
    anr_lines = [l for l in logs.splitlines() if "ANR" in l or "Application Not Responding" in l]
    
    print(f"Total relevant Unreel log entries: {len(unreel_lines)}")
    print(f"Overlay detach events: {len(detach_calls)}")
    print(f"Overlay attach events: {len(attach_calls)}")
    print(f"Reels detected / intercepted: {len(reels_detected)}")
    print(f"Modal matches: {len(modal_matches)}")
    print(f"Splash matches: {len(splash_matches)}")
    print(f"ANR warnings: {len(anr_lines)}")
    
    if detach_calls:
        print("  WARNING: Detach calls occurred during scrolling:")
        for d in detach_calls[:5]:
            print(f"    {d}")
            
    if reels_detected:
        print("  WARNING: Reels detected during scrolling:")
        for r in reels_detected[:5]:
            print(f"    {r}")
            
    if splash_matches:
        print("  WARNING: Splash detected during scrolling:")
        for s in splash_matches[:5]:
            print(f"    {s}")

    return {
        "speed": speed_name,
        "detach_count": len(detach_calls),
        "reels_detected_count": len(reels_detected),
        "splash_count": len(splash_matches),
        "modal_count": len(modal_matches),
        "anr_count": len(anr_lines)
    }

def main():
    print("Verifying device connection...")
    dev = run_adb("get-state")
    if dev != "device":
        print(f"Device not ready: {dev}")
        sys.exit(1)
        
    print("Bringing Instagram to foreground...")
    run_adb("shell monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1")
    time.sleep(2)
    
    # 1. Slow scroll
    r_slow = test_scroll("Slow Scroll", 1600, 1000, 800, count=3)
    
    # 2. Medium scroll
    r_med = test_scroll("Medium Scroll", 1600, 800, 350, count=4)
    
    # 3. Fast scroll / fling
    r_fast = test_scroll("Fast Fling", 1800, 400, 100, count=5)
    
    # 4. Rapid multi-fling
    r_rapid = test_scroll("Rapid Multi-Fling", 1900, 300, 50, count=6)
    
    # Scroll back up to return to start
    print("\nScrolling back up to top...")
    for _ in range(8):
        run_adb("shell input swipe 540 600 540 1800 150")
        time.sleep(0.15)
        
    print("\n=== SUMMARY RESULTS ===")
    for res in [r_slow, r_med, r_fast, r_rapid]:
        print(f"{res['speed']}: Detaches={res['detach_count']}, ReelsTriggered={res['reels_detected_count']}, SplashFalses={res['splash_count']}, ANRs={res['anr_count']}")

if __name__ == "__main__":
    main()
