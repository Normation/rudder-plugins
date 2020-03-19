#!/usr/bin/python3

"""
parsePDF

Parser for CIS benchmark pdfs
Usage:
    parsePDF <PATH>

PATH: path to the pdf to parse
If used as a CLI, it will print down requested infos instead

"""

import re
import subprocess
import docopt

def printItems(path):
    items = parsePDF(path)
    for i in items:
        print(i["id"] + " " + i["name"])

def parsePDF(path):
    command  = ["/usr/bin/pdftotext",  path, "-"]
    output = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True).stdout
    r = re.compile(r"""(?:(\d+\.)+\d+\s+[\S ]+\n)?(?P<id>(?:\d+\.)+\d+)\s+(?P<name>(?:([\S ]{5,})\n?([\S ])*))\(Scored\)\s*
Profile\s+Applicability:\s*(?:\d+ \| P a g e\s+)?(?P<Level>(?:\s+\W+Level\s+(1|2)[\s-]+(?:Workstation|Server)){1,2})\s+(?P<description>Description:[\S \n]+?)(?=.?Audit|.?Remediation)""")
    items = [m.groupdict() for m in r.finditer(output)]

    for iItem in items:
        # Level group is on the form:
        #<non printablechar> Level 1 - Server
        #<non printablechar> Level 1 - Workstation
        rServer = re.compile(r".?\s+Level (?P<Server>1|2) - Server")
        mServer = rServer.search(iItem["Level"])
        if mServer:
            iItem["server"] = mServer.group("Server")

        rWorkstation = re.compile(r".?\s+Level (?P<Workstation>1|2) - Workstation")
        mWorkstation = rWorkstation.search(iItem["Level"])
        if mWorkstation:
            iItem["workstation"] = mServer.group("Server")

        for k,v in iItem.items():
            if v is not None:
                iItem[k] = v.replace("\n", " ").strip()

    return items

def buildTags(data, osName):
    tags = []
    ids = data["id"].split(".")
    tags.append({"cis-" + osName: ids[0]})
    for i in range(1,len(ids)):
        ids[i] = ids[i-1] + "." + ids[i]
        tags.append({"cis-" + osName: ids[i] })

    # server level
    if "server" in data:
        tags.append({"cis-server":  data["server"]})
    # workstation level
    if "workstation" in data:
        tags.append({"cis-workstation": data["workstation"]})
    return tags

def getInfosFromPDF(items, itemName, osName):
    data = []
    for iItem in items:
        if iItem["name"] in itemName.strip():
            data = iItem
            data["tags"] = buildTags(data, osName)
    return data


if __name__ == "__main__":
  args = docopt.docopt(__doc__)
  printItems(args['<PATH>'])

