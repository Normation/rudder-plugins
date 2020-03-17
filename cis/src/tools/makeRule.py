#!/usr/bin/python3

"""
makeRule

Rule maker for CIS benchmark pdfs
Usage:
    makeRule <CONFIGURATION_PATH> <PDF_PATH> <OS_NAME>

Take a rule file under <CONFIGURATION_PATH>, and generate its
directive list based on the parsing of <PDF_PATH>.
All generated rules will be made under the folder ./target/configuration/rules


If there is no rule file found, it will create one.
"""

import json
import os
import parsePDF
import uuid
import docopt

def makeJsonFile(filename, content):
  os.makedirs(os.path.dirname(filename), exist_ok=True)
  with open(filename, "w") as fd:
    fd.write(json.dumps(content, sort_keys=True, indent=2, separators=(',', ': ')))

def is_directive(data):
    if "techniqueName" in data and "techniqueVersion" in data:
        return True
    return False

def getDirectives(configurationPath):
    directives = []
    for root, dirs, files in os.walk(configurationPath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               if is_directive(data) == True:
                   directives.append(data)
    return directives

# Generate rule for a given os based on a benchmark pdf
def makeRule(configurationPath, pdfPath, osType):
    items = parsePDF.parsePDF(pdfPath)
    expectedDirectives = set([i["name"] for i in items])
    directives = getDirectives(configurationPath)
    try:
        with open(configurationPath + "/rules/" + osType + ".json") as f:
            data = json.load(f)
    except:
        rudderUUID = str(uuid.uuid4())
        data = {
                "directives": [],
                "displayName": "CIS - " + osType + " sample rule",
                "enabled": True,
                "id": rudderUUID,
                "longDescription": "",
                "shortDescription": "",
                "system": False,
                "tags": [],
                "targets": [ {"exlude": { "or": [] }, "include": { "or": ["policyServer:root"] } } ]
                }
        makeJsonFile(configurationPath + "/rules/" + osType + ".json", data)
    for iDirective in directives:
        if any(elem in iDirective["displayName"].replace("CIS - ", "") for elem in expectedDirectives):
            data["directives"].append(iDirective["id"])
    makeJsonFile("target/configuration/rules/" + osType + ".json", data)


if __name__ == "__main__":
  args = docopt.docopt(__doc__)
  makeRule(args['<CONFIGURATION_PATH>'], args['<PDF_PATH>'], args['<OS_NAME>'])
