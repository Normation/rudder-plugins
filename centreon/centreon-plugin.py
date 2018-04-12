#!/usr/bin/env python3

import json
import configparser
import sys
import requests
import urllib3
import socket
from requests.exceptions import HTTPError
from centreonapi.webservice import Webservice
from centreonapi.webservice.configuration.host import Host
from centreonapi.webservice.configuration.hostgroups import Hostgroups

def printHelp():
    print("""
    Usage : ./centreon-plugin <option>
    Options :
        help     Prints this help
        pull     Updates rudder nodes from the rudder server API
        push     Sends last update to centreon server
        update   Pulls from rudder and pushes to centreon
    """)

def getRequestToRudderAPI(path):
    return requests.get(conf['RUDDER']['rudderAPIURL'] + path, headers={ 'X-API-Token': conf['RUDDER']['rudderAPIToken'] }, verify=False).json()

def validateIPAddr(ip):
    try:
        socket.inet_aton(ip)
        return True
    except:
        return False

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

def updateNodesJSON():
    print("[ ] Pulling data from Rudder server API...")
    data = getRequestToRudderAPI("/nodes")
    rnodes = []
    for node in data['data']['nodes']:
        rnodes.append(dictifyNode(node))
    nodes_file = open(confPath + 'rudder_nodes.json', 'w+')
    json.dump(rnodes, nodes_file)
    nodes_file.close()
    print("[+] Done")

def updateCentreonHosts():
    print("[ ] Checking if Centreon is up-to-date...")
    checkRudderCentreonHostGroup()
    centreon_hosts = Host()
    rudder_nodes = json.load(open('rudder_nodes.json'))
    for rn in rudder_nodes:
        if not any(h['name'] == rn['rudder_id'] for h in centreon_hosts.list()['result']):
            print("[ ] Unregistered Rudder node found (id " + rn['rudder_id'] + "). Adding it to Centreon...")
            centreon_hosts.add(rn['rudder_id'], "Rudder " + rn['node_type'] + " node " + rn['hostname'], rn['ip_address'], '', 'Central', '')
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

def addHostOnRudderHook(rudderID):
    data = getRequestToRudderAPI("/nodes/" + rudderID)
    node = dictifyNode(data['data']['nodes'][0])
    centreon_hosts = Host()
    if not any(h['name'] == node['rudder_id'] for h in centreon_hosts.list()['result']):
        centreon_hosts.add(node['rudder_id'], "Rudder " + node['node_type'] + " node " + node['hostname'], node['ip_address'], '', 'Central', '')
    checkIfNodeInRudderGroup(node['rudder_id'], centreon_hosts)

def delHostOnRudderHook(rudderID):
    centreon_hosts = Host()
    if any(h['name'] == rudderID for h in centreon_hosts.list()['result']):
        centreon_hosts.delete(rudderID)

if __name__ == '__main__':
    if(len(sys.argv) < 2):
        printHelp()
    else:
        confPath = "./"
        conf = configparser.ConfigParser()
        conf.read(confPath + 'conf.ini')
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        try:
            WSInstance = Webservice.getInstance(conf['CENTREON']['centreonWebserviceURL'], conf['CENTREON']['username'], conf['CENTREON']['password'])
        except HTTPError:
            print("[!] Unable to connect to Centreon webservice. Check conf.ini ?")
            sys.exit(0)

        opt = sys.argv[1]
        if opt == "help":
            printHelp()
        elif opt == "pull":
            updateNodesJSON()
        elif opt == "push":
            updateCentreonHosts()
        elif opt == "update":
            updateNodesJSON()
            updateCentreonHosts()
        elif opt == "hook":
            opt2 = sys.argv[2]
            if opt2 == "add":
                addHostOnRudderHook(sys.argv[3])
            elif opt2 == "rm":
                delHostOnRudderHook(sys.argv[3])
        else:
            print("Unknown option " + opt)
            printHelp()

