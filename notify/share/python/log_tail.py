import os, time, subprocess

def log_tail(filename):
    sp = subprocess.Popen(["tail", "-F", "-n 0", filename], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    while True:
        line = sp.stdout.readline()
        if line:
            yield line.strip()
        time.sleep(1)
