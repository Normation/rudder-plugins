#!/usr/bin/python3

"""
    Regenerate techniques for a given bench, starting from the common ones.
"""

import json
import os
import re

COMMON_TECHNIQUES = "techniques"

def canonify(string):
  string = string.encode("utf-8").decode("iso-8859-1")
  regex = re.compile("[^a-zA-Z0-9_]")
  return regex.sub("_", string)

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

def makeTechniques(osName):
    for root, dirs, files in os.walk(COMMON_TECHNIQUES):
        for file in files:
            if file.endswith('.json'):
                with open(os.path.join(root, file)) as f:
                    data = json.load(f)
                if is_technique(data) == True:
                    data["data"]["name"] = "CIS " + osName + " - " + data["data"]["name"]
                    data["data"]["category"] = canonify("CIS " + osName)
                    data["data"]["bundle_name"] = canonify(data["data"]["name"])
                makeJsonFile(osName + "/techniques/" + file, data)

def updateDirectives(osName):
    for root, dirs, files in os.walk(osName + "/directives"):
        for file in files:
            if file.endswith('.json'):
                with open(os.path.join(root, file)) as f:
                    data = json.load(f)
                if is_directive(data) == True:
                    if not data["techniqueName"].startswith(canonify("CIS " + osName + " - ")):
                        data["techniqueName"] = canonify("CIS " + osName + " - " + data["techniqueName"])
                makeJsonFile(os.path.join(root, file), data)

def removeUnusedTechniques(osName):
    # Get used Techniques
    usedTechniques = set()
    for root, dirs, files in os.walk(osName + "/directives"):
        for file in files:
            if file.endswith('.json'):
                with open(os.path.join(root, file)) as f:
                    data = json.load(f)
                if is_directive(data) == True:
                    usedTechniques.add(data["techniqueName"])
    # Get defined techniques
    for root, dirs, files in os.walk(osName + "/techniques"):
        for file in files:
            if file.endswith('.json'):
                with open(os.path.join(root, file)) as f:
                    data = json.load(f)
                if is_technique(data) == True:
                    if data["data"]["bundle_name"] not in usedTechniques:
                        os.remove(os.path.join(root, file))
                        print("Technique %s is not used and will be removed"%os.path.join(root, file))

makeTechniques("ubuntu18")
makeTechniques("redhat7")
makeTechniques("debian9")
