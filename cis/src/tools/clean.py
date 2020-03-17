#!/usr/bin/python3

"""
    Clean all json found and try to "clean" them in order to remove all
    build-dependant key-values in them
"""

import json
import os

def makeJsonFile(filename, content):
  os.makedirs(os.path.dirname(filename), exist_ok=True)
  with open(filename, "w") as fd:
    fd.write(json.dumps(content, sort_keys=True, indent=2, separators=(',', ': ')))

def clean(basePath):
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               data["tags"] = []
               data["longDescription"] = ""
               data["shortDescription"] = ""
               data.pop("description", None)
               if "directives" in data:
                   data["directives"] = []
               makeJsonFile(os.path.join(root, file), data)

clean("ubuntu18")
clean("debian9")
clean("redhat7")
