#!/usr/bin/python3

"""
findMissing

Print the detected missing directives for a given CIS benchmark
Usage:
    findMissing <OS_NAME>

OS_NAME: Name of the os you owant to tag, should be the same syntax as
         the pdf path: ./pdfs/cis_<OS_NAME>.pdf

"""

import docopt
import json
import re
import os
import parsePDF

def is_directive(data):
    if "techniqueName" in data and "techniqueVersion" in data:
        return True
    return False

def getDirectives(basePath):
    directives = []
    for root, dirs, files in os.walk(basePath):
        for file in files:
            if file.endswith('.json'):
               with open(os.path.join(root, file)) as f:
                   data = json.load(f)
               if is_directive(data) == True:
                   directives.append(data)
    return directives

def findMissing(basePath, pdfPath):
    RED = '\033[91m'
    ENDC = '\033[0m'
    GREEN = '\033[92m'

    items = parsePDF.parsePDF(pdfPath)
    directives = getDirectives(basePath)
    # Some items are split in multiples directives, marked with a ( xxxx ) displayname suffix
    for iDirective in directives:
        if re.match(r".*\(.*\)$", iDirective["displayName"]):
            iDirective["displayName"] = re.sub("\s+\(.*\)$", "", iDirective["displayName"])
    implemented = set([i["displayName"].replace("CIS - ", "") for i in directives])

    expected = set([i["name"] for i in items])
    result = list(expected - implemented)

    for i in items:
        if i["name"] in implemented:
            print(GREEN + "[OK]   " + i["id"] + " - " + i["name"] + ENDC)
        else:
            print(RED + "[FAIL] " + i["id"] + " - " + i["name"] + ENDC)
    print("%s items covered out of %s"%(len(implemented), len(expected)))



if __name__ == "__main__":
  args = docopt.docopt(__doc__)
  osName = args['<OS_NAME>']
  pdfPath = "pdfs/cis_" + osName + ".pdf"
  findMissing(osName, pdfPath)

