#!/usr/bin/env python

"""
Usage:
    centreon-plugin.py pull
    centreon-plugin.py push
    centreon-plugin.py update
    centreon-plugin.py hook (add|rm) <id>
    centreon-plugin.py commit

Options:
    pull     Updates rudder_nodes.json from the server API
    push     Updates Centreon server based on rudder_nodes.json
    update   Pulls nodes from Rudder, then pushes to Centreon
    hook     Used when automatically called from Rudder
    commit    Applies the monitoring config (templates and macros) specified in rudder
"""

import os
import json
import csv
import time
try:
  from configparser import ConfigParser
except:
  from ConfigParser import ConfigParser
import sys
import requests
import urllib3
sys.path.insert(0,"/opt/rudder/share/python")
from docopt import docopt
from requests.exceptions import HTTPError
from centreonapi.webservice import Webservice
from centreonapi.webservice.configuration.host import Host
from centreonapi.webservice.configuration.hostgroups import Hostgroups
import ipaddress

confFile = "/opt/rudder/etc/centreon.conf"
jsonTmp = "/var/rudder/plugin-resources/rudder_nodes.json"
registerFile = "/var/rudder/plugin-resources/centreon_register.conf"
systemToken = "/var/rudder/run/api-token"


# Generic call to the Rudder API, completed by its path param. Using URL and token obtained from centreon.conf
def getRequestToRudderAPI(path):
    try:
        apiURL = conf.get('RUDDER', 'rudderAPIURL')
    except:
        apiURL = "https://localhost/rudder/api/latest"
    try:
        apiToken = conf.get('RUDDER', 'rudderAPIToken')
    except:
        with open(systemToken, 'r') as fd:
            apiToken = fd.read()
    try:
        data = requests.get(apiURL + path, headers={ 'X-API-Token': apiToken }, verify=False).json()
    except: # python3 -> json.decoder.JSONDecodeError:
        print("[!] Error : check your Rudder API token")
        sys.exit(-1)
    return data

# Used to determinate which IP will be given to Centreon, from all the IPs Rudder sends us
def validateIPAddr(ip):
    if ':':
        return False
    if sys.version_info[0] == 2:
        ip = unicode(ip)
    ip_obj = ipaddress.ip_address(ip)
    if ip_obj.is_loopback():
        return False
    try:
        filter_out = conf.get('RUDDER', 'ipBlacklist')
        for net in filter_out.split(" "):
            if sys.version_info[0] == 2:
                net = unicode(net)
            net_obj = ipaddress.ip_network(net)
            if ip_obj in net_obj:
                return False
    except:
        pass
    return True

def dictifyNode(node):
    ipAddr = ''
    for ip in node['ipAddresses']:
        if validateIPAddr(ip):
            ipAddr = ip
            break
    return { 'hostname': node['hostname'], 'rudder_id': node['id'], 'node_type': 'server' if node['id'] == 'root' else 'agent', 'ip_address': ipAddr }

def checkIfNodeInRudderGroup(nodeid, centreon_hosts):
    if not any(g['name'] == 'rudder-nodes' for g in centreon_hosts.gethostgroup(nodeid)['result']):
        print("[ ] Rudder node " + nodeid + " not in Centreon host group 'rudder-nodes'. Adding it...")
        centreon_hosts.addhostgroup(nodeid, ['rudder-nodes'])

# When manually used, used for pulling
def updateNodesJSON():
    print("[ ] Pulling data from Rudder server API...")
    data = getRequestToRudderAPI("/nodes")
    rnodes = []
    for node in data['data']['nodes']:
        rnodes.append(dictifyNode(node))
    nodes_file = open(jsonTmp, 'w+')
    json.dump(rnodes, nodes_file)
    nodes_file.close()
    print("[+] Done")

# Manual pushing
def updateCentreonHosts(poller):
    print("[ ] Checking if Centreon is up-to-date...")
    checkRudderCentreonHostGroup()
    centreon_hosts = Host()
    rudder_nodes = json.load(open(jsonTmp))

    for rn in rudder_nodes:
        if not any(h['name'] == rn['hostname'] for h in centreon_hosts.list()['result']):
            print("[ ] Unregistered Rudder node found: " + rn['hostname'] + " (id " + rn['rudder_id'] + "). Adding it to Centreon...")
            centreon_hosts.add(rn['hostname'], "Rudder " + rn['node_type'] + " node " + rn['rudder_id'], rn['ip_address'], '', poller, '')
            checkIfNodeInRudderGroup(rn['hostname'], centreon_hosts)
    for ch in centreon_hosts.list()['result']:
        if any(g['name'] == 'rudder-nodes' for g in centreon_hosts.gethostgroup(ch['name'])['result']) and not any(ch['name'] == rn['hostname'] for rn in rudder_nodes):
                print("[ ] Host " + ch['name'] + " not listed in Rudder but appearing in rudder-nodes Centreon host group. Deleting host...")
                centreon_hosts.disable(ch['name'])
                centreon_hosts.delete(ch['name'])
                print("[+] Done")
    print("[+] Done")

def checkRudderCentreonHostGroup():
    centreon_hostGrps = Hostgroups()
    if not any(g['name'] == 'rudder-nodes' for g in centreon_hostGrps.list()['result']):
        print("[ ] Host group 'rudder-nodes' not found on Centreon Central server, creating it...")
        centreon_hostGrps.add("rudder-nodes", "Rudder nodes Hosts Group")

def addHostOnRudderHook(rudderID, poller):
    data = getRequestToRudderAPI("/nodes/" + rudderID)
    node = dictifyNode(data['data']['nodes'][0])
    centreon_hosts = Host()
    if not any(h['name'] == node['hostname'] for h in centreon_hosts.list()['result']):
        centreon_hosts.add(node['hostname'], "Rudder " + node['node_type'] + " node " + node['rudder_id'], node['ip_address'], '', poller, '')
    checkIfNodeInRudderGroup(node['hostname'], centreon_hosts)

def delHostOnRudderHook(rudderID):
    centreon_hosts = Host()
    if any(h['name'] == rudderID for h in centreon_hosts.list()['result']):
        centreon_hosts.disable(rudderID)
        centreon_hosts.delete(rudderID)

#When we add a template, we register it to prevent us from deleting templates we did not add when they do not appear in Rudder
def registerTemplate(node, template, register):
    if not register.has_section(node):
        register.add_section(node)
    if not register.has_option(node, 'templates'):
        register.set(node, 'templates', template)
    else:
        register.set(node, 'templates', register.get(node,'templates') + ',' + template)

def templateWasRegistered(node, template, register):
    return register.has_section(node) and template in register.get(node,'templates').split(',')

def unregisterTemplate(node, template, register):
    tlist = register.get(node,'templates').split(',')
    tlist.remove(template)
    register.set(node, 'templates',  ','.join(tlist))

#Same for macros
def registerMacro(node, macroKey, macroValue, register):
    if not register.has_section(node):
        register.add_section(node)
    register.set(node, macroKey, macroValue)

def macroWasRegistered(node, macroKey, register):
    return register.has_section(node) and register.has_option(node, macroKey)

def unregisterMacro(node, macroKey, register):
    register.remove_option(node, macroKey)

def applyRudderMonitoringConfigurations(conf):
    centreon_hosts = Host()
    register = ConfigParser()
    register.read(registerFile)
    changed = False

    if os.path.exists('/var/rudder/shared-files/root/files'):
        # list all nodes
        nodes_list = {}
        data = getRequestToRudderAPI("/nodes?include=minimal")
        for node in data['data']['nodes']:
            nodes_list[node["id"]] = node["hostname"]
    
        for dir in next(os.walk('/var/rudder/shared-files/root/files'))[1]:
            name = nodes_list[dir]
            try:
                confcsv = csv.reader(open('/var/rudder/shared-files/root/files/' + dir + '/rudder_monitoring.csv'))
            except:
                print('[!] Node ' + name + ' has no rudder monitoring config file, considering it empty...')
                confcsv = []
    
            if not any(h['name'] == name for h in centreon_hosts.list()['result']):
                print('[!] Node ' + name + ' is not registered in Centreon, skipping...')
                continue
    
            print('[ ] Applying conf to node ' + name + '...')
            csvTmpList = []
            csvMacroKeyList = []
            for r in confcsv:
                if(r[0] == 'template'):
                    csvTmpList.append(r[1])
                    if not any(t['name'] == r[1] for t in centreon_hosts.gettemplate(name)['result']):
                        try:
                            centreon_hosts.addtemplate(name, r[1])
                            centreon_hosts.applytemplate(name)
                            registerTemplate(name, r[1], register)
                            changed = True
                        except HTTPError:
                            print('[!] Centreon API error, check if template ' + r[1] + ' exists')
                    else:
                        print('[ ] Template ' + r[1] + ' already applied to node ' + name)
                        registerTemplate(name, r[1], register)
                elif(r[0] == 'param'):
                    csvMacroKeyList.append(r[1])
                    centreon_hosts.setmacro(name, r[1], ''.join(r[2:]))
                    registerMacro(name, r[1], ''.join(r[2:]), register)
                    changed = True
                else:
                    print('[!] Incorrect config parameter type ' + r[0] + ', skipping...')
    
            for t in centreon_hosts.gettemplate(name)['result']:
                if not any(t['name'] == n for n in csvTmpList) and templateWasRegistered(name, t['name'], register):
                    unregisterTemplate(name, t['name'], register)
                    centreon_hosts.deletetemplate(name, t['name'])
                    changed = True
    
            for mk in centreon_hosts.getmacro(name)['result']:
                if not any(mk['macro name'] == n for n in csvMacroKeyList) and macroWasRegistered(name, mk['macro name'], register):
                    unregisterMacro(name, mk['macro name'], register)
                    centreon_hosts.deletemacro(name, mk['macro name'])
                    changed = True
    
            #This ensures we get no duplicates in register in case we have to re-add an already registered
            #register[dir]['templates'] = ','.join(list(set(register[dir]['templates'].split(','))))

    if changed:
        register.write(open(registerFile, 'w'))
        print('[ ] Reloading Centreon poller config, restarting poller...')
        Webservice.getInstance().restart_poller(conf.get('CENTREON', 'centreonPoller'))
    print('[+] Done')

if __name__ == '__main__':
    args = docopt(__doc__)
    conf = ConfigParser()
    conf.read(confFile)
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    try:
        WSInstance = Webservice.getInstance(conf.get('CENTREON', 'centreonWebserviceURL'), conf.get('CENTREON', 'username'), conf.get('CENTREON', 'password'))
        WSInstance.auth()
    except HTTPError:
        print("[!] Unable to connect to Centreon webservice. Check centreon.conf ?")
        sys.exit(1)

    if(args['pull']):
        updateNodesJSON()
    elif(args['push']):
        updateCentreonHosts(conf.get('CENTREON', 'centreonPoller'))
    elif(args['update']):
        updateNodesJSON()
        updateCentreonHosts(conf.get('CENTREON', 'centreonPoller'))
    elif(args['hook']):
        if(args['add']):
            addHostOnRudderHook(args['<id>'], conf.get('CENTREON', 'centreonPoller'))
        elif(args['rm']):
            delHostOnRudderHook(args['<id>'])
    elif(args['commit']):
        applyRudderMonitoringConfigurations(conf)
