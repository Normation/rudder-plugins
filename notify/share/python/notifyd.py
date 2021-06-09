#!/usr/bin/env python
import sys, os, time, atexit, signal, configparser, logging
sys.path.insert(0, "/opt/rudder/share/python")
import notify

FIFO_PIPE = "/var/run/rudder-notifyd.fifo"

def start_worker():
  try:
    logging.basicConfig(filename='/var/log/rudder/notify.log', level=logging.INFO, format='%(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p', maxBytes=10*1024*1024, backupCount=5)
    logging.info('Starting the notify Rudder plugin')
    conf = configparser.ConfigParser()
    conf.read("/opt/rudder/etc/notify.conf")
    w = notify.NotifyWorker(FIFO_PIPE, conf)
    w.start()
  except Exception as e:
    logging.error("An error occurred in the worker: " + str(e))


start_worker()
