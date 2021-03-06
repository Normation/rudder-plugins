#!/usr/bin/python3

"""
trackConfig

Generate a json containing the list of rudder objects in a given
export configuration tree folder.
Usage:
    trackConfig <CONFIG_PATH> <DST>

CONFIG_PATH: path to the configuration folder to track
DST: destination path

"""

import json
import re
import os, sys
import subprocess
import uuid
import docopt

def makeJsonFile(filename, content):
  os.makedirs(os.path.dirname(filename), exist_ok=True)
  with open(filename, "w") as fd:
    fd.write(json.dumps(content, sort_keys=True, indent=2, separators=(',', ': ')))

def is_technique(data):
    if "type" in data and "version" in data and "data" in data:
        return True
    return False

def is_directive(data):
    if "techniqueName" in data and "techniqueVersion" in data:
        return True
    return False

def is_rule(data):
    if "directives" in data and "targets" in data:
        return True
    return False

def getTechniques(basePath):
    techniques = []
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
                with open(os.path.join(root, file)) as f:
                    data = json.load(f)
                if is_technique(data) == True:
                    techniques.append(file)
    return techniques

def getDirectives(basePath):
    directives = []
    print(basePath)
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               if is_directive(data) == True:
                   directives.append(data["id"])
    return directives

def getRules(basePath):
    rules = []
    print(basePath)
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               if is_rule(data) == True:
                   rules.append(data["id"])
    return rules

def trackTechniques(basePath, dst):
    try:
        with open(dst) as f:
            data = json.load(f)
    except:
        data = {}
    data["techniques"] = getTechniques(basePath)
    makeJsonFile(dst, data)

def trackDirectives(basePath, dst):
    try:
        with open(dst) as f:
            data = json.load(f)
    except:
        data = {}
    data["directives"] = getDirectives(basePath)
    makeJsonFile(dst, data)

def trackRules(basePath, dst):
    try:
        with open(dst) as f:
            data = json.load(f)
    except:
        data = {}
    data["rules"] = getRules(basePath)
    makeJsonFile(dst, data)

def trackConfig(basePath, dst):
    trackTechniques(basePath, dst)
    trackDirectives(basePath, dst)
    trackRules(basePath, dst)

if __name__ == "__main__":
  args = docopt.docopt(__doc__)
  trackConfig(args['<CONFIG_PATH>'], args['<DST>'])

