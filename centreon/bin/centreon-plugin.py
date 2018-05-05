#!/usr/bin/env python

"""
Usage:
    centreon-plugin.py pull
    centreon-plugin.py push
    centreon-plugin.py update
    centreon-plugin.py hook (add|rm) <id>

Options:
    pull     Updates rudder_nodes.json from the serve API
    push     Updates Centreon server based on rudder_nodes.json
    update   Pulls nodes from Rudder, then pushes to Centreon
    hook     Used when automatically called from Rudder
"""

import json
import configparser
import sys
import requests
import urllib3
sys.path.insert(0,"/opt/rudder/share/python")
from docopt import docopt
from requests.exceptions import HTTPError
from centreonapi.webservice import Webservice
from centreonapi.webservice.configuration.host import Host
from centreonapi.webservice.configuration.hostgroups import Hostgroups

confFile = "/opt/rudder/etc/centreon.conf"
jsonTmp = "/var/rudder/tmp/rudder_nodes.json"

# Generic call to the Rudder API, completed by its path param. Using URL and token obtained from centreon.conf
def getRequestToRudderAPI(path):
    try:
        data = requests.get(conf['RUDDER']['rudderAPIURL'] + path, headers={ 'X-API-Token': conf['RUDDER']['rudderAPIToken'] }, verify=False).json()
    except json.decoder.JSONDecodeError:
        print("[!] Error : check your Rudder API token")
        sys.exit(-1)
    return data

# Used to determinate which IP will be given to Centreon, from all the IPs Rudder sends us
def validateIPAddr(ip):
    return not ':' in ip and not ip == '127.0.0.1'

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
        if not any(h['name'] == rn['rudder_id'] for h in centreon_hosts.list()['result']):
            print("[ ] Unregistered Rudder node found (id " + rn['rudder_id'] + "). Adding it to Centreon...")
            centreon_hosts.add(rn['rudder_id'], "Rudder " + rn['node_type'] + " node " + rn['hostname'], rn['ip_address'], '', poller, '')
            checkIfNodeInRudderGroup(rn['rudder_id'], centreon_hosts)
    for ch in centreon_hosts.list()['result']:
        if any(g['name'] == 'rudder-nodes' for g in centreon_hosts.gethostgroup(ch['name'])['result']) and not any(ch['name'] == rn['rudder_id'] for rn in rudder_nodes):
                print("[ ] Host " + ch['name'] + " not listed in Rudder but appearing in rudder-nodes Centreon host group. Deleting host...")
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
    if not any(h['name'] == node['rudder_id'] for h in centreon_hosts.list()['result']):
        centreon_hosts.add(node['rudder_id'], "Rudder " + node['node_type'] + " node " + node['hostname'], node['ip_address'], '', poller, '')
    checkIfNodeInRudderGroup(node['rudder_id'], centreon_hosts)

def delHostOnRudderHook(rudderID):
    centreon_hosts = Host()
    if any(h['name'] == rudderID for h in centreon_hosts.list()['result']):
        centreon_hosts.delete(rudderID)

if __name__ == '__main__':
    args = docopt(__doc__)
    conf = configparser.ConfigParser()
    conf.read(confFile)
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    
    try:
        WSInstance = Webservice.getInstance(conf['CENTREON']['centreonWebserviceURL'], conf['CENTREON']['username'], conf['CENTREON']['password'])
    except HTTPError:
        print("[!] Unable to connect to Centreon webservice. Check centreon.conf ?")
        sys.exit(1)
    
    if(args['pull']):
        updateNodesJSON()
    elif(args['push']):
        updateCentreonHosts(conf['CENTREON']['centreonPoller'])
    elif(args['update']):
        updateNodesJSON()
        updateCentreonHosts(conf['CENTREON']['centreonPoller'])
    elif(args['hook']):
        if(args['add']):
            addHostOnRudderHook(args['<id>'], conf['CENTREON']['centreonPoller'])
        elif(args['rm']):
            delHostOnRudderHook(args['<id>'])
