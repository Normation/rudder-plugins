#!/usr/bin/env python

"""
Usage:
    zabbix-plugin.py update
    zabbix-plugin.py hook addHost <id>
    zabbix-plugin.py hook rmHost <host>
    zabbix-plugin.py apply-configuration
"""

# -*- coding: utf-8 -*-
import traceback
import os
import os.path
import sys
import csv
import json
import requests
import configparser
import urllib3
sys.path.insert(0, "/opt/rudder/share/python")
from docopt import docopt
from pyzabbix import ZabbixAPI
from pprint import pprint
from requests.exceptions import HTTPError


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
    zapi.host.create(host=node["hostname"], groups=[{"groupid":zabbixGroupID(zapi, "Rudder nodes")}], interfaces=[{"type":"1", "main":"1", "useip":"1", "ip":nodeip, "dns":"", "port":"10050"}],description=node["id"]) 

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

    
# The ConfigParser lowercases all keys and section names, and Zabbix uppercases macro keys, hence this overriding to keep things nice
class MyConfigParser(configparser.ConfigParser):
    optionxform = lambda self, optionstr : optionstr

if __name__ == "__main__":
    confFile = "/opt/rudder/etc/zabbix.conf"
    nodesTmp = "/var/ruuder/plugin-ressources/rudder_nodes.json"
    templatesTmp = "/var/rudder/plugin-ressources/rudder_templates.json"
    registerFile = "/var/rudder/plugin-resources/zabbix_register.conf"
    conf = MyConfigParser()
    conf.read(confFile)
    register = MyConfigParser()
    register.read(registerFile)
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    
    try:
        zapi = ZabbixAPI(conf['ZABBIX']['ZabbixWebserviceURL'])
        zapi.login(conf['ZABBIX']['username'], conf['ZABBIX']['password'])
        print("Connected to Zabbix API Version %s" % zapi.api_version())             
        ztemplates = zapi.template.get()
           
    except:
        print(traceback.print_exc())
        print("[!] Unable to connect to the Zabbix API. Check your zabbix.conf")
        sys.exit(1)

    args = docopt(__doc__)
    if args['update']:
        update(conf, register, zapi)
        register.write(open(registerFile, "w"))
        print("[+] Done.")

    # adding new rudder node in zabbix hosts
    elif args["hook"]:
        if args["addHost"]: 
            node = getRequestToRudderAPI(conf, "/nodes/" + args["<id>"])["data"]["nodes"][0]
            zhosts = zapi.host.get()
            if node["hostname"] not in (h["host"] for h in zhosts): 
                addZabbixHost(zapi, node)
                print("[ ] Adding node " + node["hostname"] +" to Zabbix...")
                if not register.has_section(node["hostname"]):
                    register.add_section(node["hostname"])

        # delete host from zabbix if host is absent in rudder server
        elif args["rmHost"]:
            zhosts = zapi.host.get()
            rnodes = getRequestToRudderAPI(conf, "/nodes")["data"]["nodes"]
            for host in zapi.host.get(output="extend"):       
                if host['host'] not in (node["hostname"] for node in rnodes):
                    zapi.host.delete(zabbixHostID(host["host"], zhosts))
                    print("[ ] Removing node " + host["host"] + " from Zabbix...")                
                    if register.has_section(host["host"]):
                        register.remove_section(host["host"])

    # apply-configuration ( hosts - Templates - Macros) 
    elif args["apply-configuration"]:
        rootPath = '/var/rudder/shared-files/root/files'
        zabbixCsv = '/var/rudder/shared-files'
        zhosts = zapi.host.get()
        rnodes = getRequestToRudderAPI(conf, "/nodes")["data"]["nodes"]
        ztemplates = zapi.template.get()
        
        # save all rudder nodes related templates to a csv file " /var/rudder/shared-files/rudderMonitor.csv "
        os.chdir('/var/rudder/shared-files')
        with open('rudderMonitor.csv', 'wb') as myfile:
            if os.path.exists('rudderMonitor.csv'):
                c = csv.writer(open("rudderMonitor.csv", "wb"))
            else:
                print('[!] Node ' + name + ' has no rudder monitoring config file, considering it empty...')
            
        for host in zapi.host.get(output="extend"):
            if host['host'] in (node["hostname"] for node in rnodes):
                rtemplates = zapi.do_request("host.get", params={"output":"extend", "selectParentTemplates": ["templateid", "name"], "hostids": host['hostid'] })
                rmacros = zapi.do_request("host.get", params={"output":"extend", "selectMacros": ["hostmacroid", "macro", "value"], "hostids": host['hostid'] }) 
                ztemplates = zapi.template.get()
                
                for result in rtemplates["result"]:
                    for parentTemplate in result['parentTemplates']:
                        c.writerow([host['host'],parentTemplate["name"]])
                        if parentTemplate["name"] in (template["name"] for template in ztemplates):    
                            data = zapi.do_request("template.get",params={"output":"extend", "filter": { "host": parentTemplate["name"] }})
                            
                for result in rmacros['result']:
                    for m in result['macros']:
                        if result['host'] in (node["hostname"] for node in rnodes):
                            c.writerow([result['host'],m['macro']])
                        else:
                            print('[!] Node ' + result['host'] + ' does not exist among rudder nodes')


        if os.path.exists(rootPath):
            for name in os.listdir(rootPath):
                if name in (node["id"] for node in rnodes):
                    print('[!] '  + name + ' is an id for Rudder node applying configuration ... ')
                    
            for dir in next(os.walk(rootPath))[1]:
                csvfile = rootPath + '/' + dir + '/rudder_monitoring.csv'
                if os.path.exists(csvfile):
                    with open(csvfile) as fd:
                        # the file must stay open
                        fd = open(csvfile)
                        confcsv = csv.reader(fd)
                        conversionID = getRequestToRudderAPI(conf, "/nodes")["data"]["nodes"]
                        if name in (node["id"] for node in conversionID):
                            node = getRequestToRudderAPI(conf, "/nodes/" + name) 
                            for data in node["data"]["nodes"]:
                                hostname = data["hostname"]
                                if hostname in (h["host"] for h in zhosts):
                                    host = zapi.do_request("host.get",params={"output":"extend", "filter": { "host": hostname}})
                                    for result in host["result"]:
                                        hostid = result["hostid"]
                        print('-------------------------------------------')
                       
                       
                        print('[ ] Applying conf to node ' + hostname + '...')

                        rudderListTmp = []
                        zabbixListTmp = []
                        rudderListTmpMacros = []

                        # In RUDDER, we can edit technique editors, assign them to a node via rule and eventually monitor these directives by zabbix. 
                        
                        for row in confcsv:
                            # Templates
                            if (row[0] == 'template'):
                                template = zapi.do_request("template.get",params={"output":"extend", "filter": { "host": [row[1]]}})
                                rudderListTmp.append(row[1])
                                
                                # When monitoring template is added to the node, we link this zabbix template to the host zabbix.
                                for result in rtemplates['result']:
                                    if not any(parentTemplate["name"] == row[1] for parentTemplate in result['parentTemplates']):
                                        for result in template["result"]:
                                            templateidADD = result["templateid"]
                                        linkTemplate = zapi.do_request("template.massadd", params={"output":"extend", "templates": [{ "templateid": templateidADD }], "hosts":[{"hostid": hostid }]})
                                        print('[ ] Applying conf : the host ' + hostid + ' is linked to the template ' + templateidADD + ' ...') 
                            
                            # Macros
                            if (row[0] == 'param'):
                                rudderListTmp.append(row[1])
                                rudderListTmpMacros=rudderListTmp
                                rudderListTmpMacros[-1] = "{$"+rudderListTmpMacros[-1].upper()+"}"
                                
                                # When monitoring parameter is added to the node, we create a host macro in zabbix
                                for result in rmacros['result']:
                                    if not any(m['macro'] == row[1] for m in result['macros']):
                                        try:
                                            createUserMacro = zapi.do_request("usermacro.create", params={"hostid": hostid, "macro" : "{$" + row[1] + "}", "value": row[2] })
                                            print('[ ] Applying conf : the host ' + hostid + ' has a new parameter ' + "{$" + row[1] + "}" + ' ...')
                                        except:
                                            print('[ ] Macro ' + row[1] + ' already added to node ' + result['host'] + ' ...')
                                   
                            for result in rtemplates["result"]:
                                for parentTemplate in result['parentTemplates']:
                                    nameT = parentTemplate["name"]
                        
                            for result in rmacros["result"]:
                                for m in result['macros']:
                                    nameM = m['macro']
                        
                        
                        zabbixListTmp.append(nameT) 
                        zabbixListTmp.append(nameM)
                                   
                        
                          
                        print("--------------------------------")
                        resTemplates = [item for item in zabbixListTmp if not item in rudderListTmp]
                        resMacros = [item for item in zabbixListTmp if not item in rudderListTmpMacros]
                        template = zapi.do_request("template.get",params={"output":"extend", "filter": { "host": resTemplates }})
                        
                        
                        # When a monitoring template is removed from rudder node, we unlike this template from zabbix host 
                        listnames = []
                        for result in template["result"]:
                            templateidRM = result["templateid"]
                            if(resTemplates):
                                unlinkTemplate = zapi.do_request("template.massremove", params={"output":"extend", "templateids": templateidRM , "hostids": hostid })   
                                print('[ ] Applying conf: the host ' + hostid + ' is unlinked to the template ' + templateidRM + ' ...')
                             
                        # When a monitoring parameter is removed from rudder node, we remove macro from zabbix host 
                        for result in rmacros['result']:
                            for m in result['macros']:
                                listnames.append(m)    
                            for line in listnames:
                                for item in resMacros:
                                    if (line['macro']==item):
                                        macroid = line['hostmacroid']
                                        if(resMacros):
                                             deleteUserMacros = zapi.do_request("usermacro.delete", params=[ macroid ])
                                             print('[ ] Applying conf : Macro ' + item + ' is removed to the host ' + result['host'] + ' ...' )
         

                        print("[+] Done.")        
                else:
                    print('[!] Node ' + name + ' has no rudder monitoring config file, considering it empty...')
        

register.write(open(registerFile, "w")) 
