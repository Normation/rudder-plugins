#!/usr/bin/python3

"""
cisTag

Script to generate configurations taken from a CIS benchmark
Usage:
    cisTag <OS_NAME>

OS_NAME: Name of the os you owant to tag, should be the same syntax as
         the pdf path: ./pdfs/cis_<OS_NAME>.pdf

The script take the files directives found under the folder <OS_NAME>,
the pdf under ./pdfs/cis_<OS_NAME>.pdf and generate under the folder
./target/configuration/directives a set of directives with customized
params such as:
    - displayName
    - tags
    - short/longDescription
    - policyMode
"""

import docopt
import json
import os
import parsePDF

def makeJsonFile(filename, content):
  os.makedirs(os.path.dirname(filename), exist_ok=True)
  with open(filename, "w") as fd:
    fd.write(json.dumps(content, sort_keys=True, indent=2, separators=(',', ': ')))

def mergeDicts(x, y):
    z = x.copy()
    z.update(y)
    return z

def is_directive(data):
    if "techniqueName" in data and "techniqueVersion" in data:
        return True
    return False

def writeDirective(items, directivePath, dstPath, osName):
    with open(directivePath) as f:
        data = json.load(f)

    if is_directive(data) == True:
        dataInfos = parsePDF.getInfosFromPDF(items, data["displayName"], osName)
        if dataInfos != []:
            # Tags
            if "tags" in dataInfos:
                data["tags"] = dataInfos["tags"]

            # Descriptions
            if "description" in dataInfos:
                data["longDescription"] = dataInfos["description"]

            # Name
            # Name from the src files should follow the syntax:
            #   CIS - Title
            # Resulting directive will follow:
            # CIS - <Item Number> - <OS Name> - Title
            array = data["displayName"].split("-")
            data["displayName"] = array[0] + "- " + dataInfos["id"] + " - " + osName + " -" + array[1]
            data["policyMode"] = "audit"
            makeJsonFile(dstPath + "/" + os.path.basename(directivePath), data)
        else:
            print("%s is most likely unwanted in the directives folder"%directivePath)

def writeAllCis(srcPath, dstPath, pdfPath, osName):
    items = parsePDF.parsePDF(pdfPath)
    for root, dirs, files in os.walk(srcPath):
        for file in files:
            if file.endswith('.json'):
                writeDirective(items, os.path.join(root, file), dstPath, osName)

if __name__ == "__main__":
  args = docopt.docopt(__doc__)
  osName = args['<OS_NAME>']
  pdfPath = "pdfs/cis_" + osName + ".pdf"
  srcPath = osName + "/directives"
  dstPath = "target/configuration/directives"
  writeAllCis(srcPath, dstPath, pdfPath, osName)

