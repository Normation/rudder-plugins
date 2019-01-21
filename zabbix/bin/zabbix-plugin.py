#!/usr/bin/env python

"""
Usage:
    zabbix-plugin.py update
    zabbix-plugin.py hook (add|rm) <id>
"""

import os
import sys
import csv
import requests
import configparser
import urllib3
sys.path.insert(0, "/opt/rudder/share/python")
from docopt import docopt
from pyzabbix import ZabbixAPI

# Used to determine which IP will be given to Zabbix, from all the IPs Rudder sends us (any v4 that's not 127.0.0.1)
validateIPAddr = lambda ip : not ':' in ip and not ip == '127.0.0.1'

# Gets the groupid given the name of group, creating it if it does not exist.
zabbixGroupID = lambda zapi, groupName : zapi.hostgroup.create(name=groupName)["groupids"][0] if not any(g["name"] == "Rudder nodes" for g in zapi.hostgroup.get()) else list(h["groupid"] for h in zapi.hostgroup.get() if h["name"] == groupName)[0]

# Gets the hostid given the name of host
zabbixHostID = lambda name, zhosts : list(h["hostid"] for h in zhosts if h["name"] == name)[0]

# Gets the macroid given the name of the macro
zabbixMacroID = lambda name, zmacros : list(h["hostmacroid"] for h in zmacros if h["macro"] == name)[0]

canonifyMacroKey = lambda key : "{$" + key.upper() + "}"

# Generic call to the Rudder API, completed by its path param. Using URL and token obtained from zabbix.conf
def getRequestToRudderAPI(conf, path):
    try:
        data = requests.get(conf["RUDDER"]["rudderAPIURL"] + path, headers={ 'X-API-Token': conf['RUDDER']['rudderAPIToken'] }, verify=False).json()
    except:
        print("[!] Error : check your Rudder API token")
        sys.exit(-1)
    return data

def addZabbixHost(zapi, node):
    nodeip = ""
    for ip in node["ipAddresses"]:
        if validateIPAddr(ip):
            nodeip = ip
            break

    zapi.host.create(host=node["hostname"], groups=[{"groupid":zabbixGroupID(zapi, "Rudder nodes")}], interfaces=[{"type":"1", "main":"1", "useip":"1", "ip":nodeip, "dns":"", "port":"10050"}]) 

def update(conf, register, zapi):
    zhosts = zapi.host.get()
    zmacros = zapi.do_request("usermacro.get", params=["selectHosts"])
    rnodes = getRequestToRudderAPI(conf, "/nodes")["data"]["nodes"]

    # Add all nodes from Rudder to Zabbix and to the register upon addition
    for node in rnodes:
        if node["hostname"] not in (h["host"] for h in zhosts):
            addZabbixHost(zapi, node)
            print("[ ] Adding node " + node["hostname"] +" to Zabbix...")
        if not register.has_section(node["hostname"]):
            register.add_section(node["hostname"])

    # Delete every Zabbix host that's not in Rudder, but in the register; then delete it from the register
    for host in zhosts:
        if host["host"] not in (node["hostname"] for node in rnodes) and host["host"] in register.sections():
            print("[ ] Removing node " + host["host"] + " from Zabbix...")
            zapi.host.delete(zabbixHostID(host["host"], zhosts))
            register.remove_section(host["host"])

    # Apply macros to the Zabbix hosts, register them. We need to update our host list before doing so
    zhosts = zapi.host.get()
    for rep in next(os.walk("/var/rudder/shared-files/root/files"))[1]:
        try:
            confcsv = csv.reader(open("/var/rudder/shared-files/root/files/" + rep + "/rudder_monitoring.csv"))
        except:
            print("[!] Node " + rep + " has no rudder monitoring config file, considering it empty")
            continue
        
        if rep not in (h["host"] for h in zhosts):
            print("[!] Node " + rep + " is not registered in Zabbix, skipping")
            continue

        print("[ ] Applying conf to node " + rep + "...")
        host_macros = list(zm for zm in zmacros["result"] if zm["hostid"] == zabbixHostID(rep, zhosts))
        rudder_macro_keys = []
        for r in confcsv:
            if(r[0] == "param"):
                key = canonifyMacroKey(r[1])
                rudder_macro_keys.append(key)
                val = ''.join(r[2:]) # In case the macro value contains our separator
                
                if key not in (hm["macro"] for hm in host_macros):
                    zapi.usermacro.create(hostid=zabbixHostID(rep, zhosts), macro=key, value=val)
                    register.set(rep, key, val)

                elif list(hm for hm in host_macros if hm["macro"] == key)[0]["value"] != val:
                    zapi.usermacro.update(hostmacroid=zabbixMacroID(key, zmacros["result"]), macro=key, value=val)
                    register.set(rep, key, val)

        # Delete every Zabbix macro that's not in Rudder, but in the register; then delete it from the register
        macros_to_delete = []
        for macro in host_macros:
            if macro["macro"] not in rudder_macro_keys and register.has_option(rep, macro["macro"]):
                macros_to_delete.append(macro)
                register.remove_option(rep, macro["macro"])
                zapi.usermacro.delete(zabbixMacroID(macro["macro"], zmacros["result"]))

# The ConfigParser lowercases all keys and section names, and Zabbix uppercases macro keys, hence this overriding to keep things nice
class MyConfigParser(configparser.ConfigParser):
    optionxform = lambda self, optionstr : optionstr

if __name__ == "__main__":
    confFile = "/opt/rudder/etc/zabbix.conf"
    registerFile = "/var/rudder/plugin-resources/zabbix_register.conf"
    conf = MyConfigParser()
    conf.read(confFile)
    register = MyConfigParser()
    register.read(registerFile)
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    
    try:
        zapi = ZabbixAPI(conf["ZABBIX"]["ZabbixWebserviceURL"])
        zapi.login(conf["ZABBIX"]["username"], conf["ZABBIX"]["password"])
    except:
        print("[!] Unable to connect to the Zabbix API. Check your zabbix.conf")
        sys.exit(1)

    args = docopt(__doc__)
    if args["update"]:
        update(conf, register, zapi)
        register.write(open(registerFile, "w"))
        print("[+] Done.")

    elif args["hook"]:
        if args["add"]:
            node = getRequestToRudderAPI(conf, "/nodes/" + args["<id>"])["data"]["nodes"][0]
            zhosts = zapi.host.get()
            if node["hostname"] not in (h["host"] for h in zhosts):
                addZabbixHost(zapi, node)
                if not register.has_section(node["hostname"]):
                    register.add_section(node["hostname"])

        elif args["rm"]:
            zhosts = zapi.host.get()
            node = args["<id>"]
            if register.has_section(node):
                register.remove_section(node)
            if node in (h["host"] for h in zhosts):
                zapi.host.delete(zabbixHostID(node, zhosts))

        register.write(open(registerFile, "w"))
