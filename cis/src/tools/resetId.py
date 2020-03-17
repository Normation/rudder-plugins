#!/usr/bin/python3

"""
    Regenerate id for all Rudder json found, it will also rename the files.
"""

import json
import os
import uuid

def makeJsonFile(filename, content):
  os.makedirs(os.path.dirname(filename), exist_ok=True)
  with open(filename, "w") as fd:
    fd.write(json.dumps(content, sort_keys=True, indent=2, separators=(',', ': ')))

def reset(basePath):
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               if "id" in data:
                   data["id"] = str(uuid.uuid4())
               os.remove(os.path.join(root, file))
               makeJsonFile(os.path.join(root, data["id"] + ".json"), data)

