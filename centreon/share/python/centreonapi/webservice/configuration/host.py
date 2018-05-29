# -*- coding: utf-8 -*-

from centreonapi.webservice import Webservice

class HostObj(object):

    def __init__(self, properties):
        self.name = properties['name']
        self.state = properties['activate']
        self.address = properties['address']
        self.alias = properties['alias']

    def name(self):
        return self.name

    def address(self):
        return self.address()

    def alias(self):
        return self.alias()

    def state(self):
        return self.state()


class Host(object):
    """
    Centreon Web host object
    """

    def __init__(self):
        """
        Constructor
        """
        self.webservice = Webservice.getInstance()

    def list(self):
        """
        List hosts
        """
        return self.webservice.call_clapi('show', 'HOST')

    def add(self, hostname, hostalias, hostip, hosttemplate, pollername, hgname):
        """
        Add a host
        """
        values = [
            hostname,
            hostalias,
            hostip,
            '|'.join(hosttemplate),
            pollername,
            '|'.join(hgname)
        ]
        return self.webservice.call_clapi('add', 'HOST', values)

    def delete(self, hostname):
        return self.webservice.call_clapi('del', 'HOST', hostname)

    def setparameters(self, hostname, name, value):
        """
        DEPRECATED
        """
        return self.setparam(hostname, name, value)

    def setparam(self, hostname, name, value):
        values = [hostname, name, value]
        return self.webservice.call_clapi('setparam', 'HOST', values)

    def setinstance(self, hostname, instance):
        values = [hostname, instance]
        return self.webservice.call_clapi('setinstance', 'HOST', values)

    def getmacro(self, hostname):
        return self.webservice.call_clapi('getmacro', 'HOST', hostname)

    def setmacro(self, hostname, name, value):
        values = [hostname, name, value]
        return self.webservice.call_clapi('setmacro', 'HOST', values)

    def deletemacro(self, hostname, name):
        values = [hostname, name]
        return self.webservice.call_clapi('delmacro', 'HOST', values)

    def gettemplate(self, hostname):
        return self.webservice.call_clapi('gettemplate', 'HOST', hostname)

    def settemplate(self, hostname, template):
        values = [hostname, "|".join(template)]
        return self.webservice.call_clapi('settemplate', 'HOST', values)

    def addtemplate(self, hostname, template):
        values = [hostname, template]
        return self.webservice.call_clapi('addtemplate', 'HOST', values)

    def deletetemplate(self, hostname, template):
        values = [hostname, template]
        return self.webservice.call_clapi('deltemplate', 'HOST', values)

    def applytemplate(self, hostname):
        """
        Apply the host template to the host, deploy services
        """
        return self.webservice.call_clapi('applytpl', 'HOST', hostname)

    def getparent(self, hostname):
        return self.webservice.call_clapi('getparent', 'HOST', hostname)

    def addparent(self, hostname, parents):
        return self.webservice.call_clapi('addparent', 'HOST', [hostname, "|".join(parents)])

    def setparent(self, hostname, parents):
        return self.webservice.call_clapi('setparent', 'HOST', [hostname, "|".join(parents)])

    def deleteparent(self, hostname, parents):
        return self.webservice.call_clapi('delparent', 'HOST', [hostname, "|".join(parents)])

    def getcontactgroup(self, hostname):
        return self.webservice.call_clapi('getcontactgroup', 'HOST', hostname)

    def addcontactgroup(self, hostname, contactgroups):
        return self.webservice.call_clapi('addcontactgroup', 'HOST', [hostname, "|".join(contactgroups)])

    def setcontactgroup(self, hostname, contactgroups):
        return self.webservice.call_clapi('setcontactgroup', 'HOST', [hostname, "|".join(contactgroups)])

    def deletecontactgroup(self, hostname, contactgroups):
        return self.webservice.call_clapi('delcontactgroup', 'HOST', [hostname, "|".join(contactgroups)])

    def getcontact(self, hostname):
        return self.webservice.call_clapi('getcontact', 'HOST', hostname)

    def addcontact(self, hostname, contacts):
        return self.webservice.call_clapi('addcontact', 'HOST', [hostname, "|".join(contacts)])

    def setcontact(self, hostname, contacts):
        return self.webservice.call_clapi('setcontact', 'HOST', [hostname, "|".join(contacts)])

    def deletecontact(self, hostname, contacts):
        return self.webservice.call_clapi('delcontact', 'HOST', [hostname, "|".join(contacts)])

    def gethostgroup(self, hostname):
        return self.webservice.call_clapi('gethostgroup', 'HOST', hostname)

    def addhostgroup(self, hostname, hostgroups):
        return self.webservice.call_clapi('addhostgroup', 'HOST', [hostname, "|".join(hostgroups)])

    def sethostgroup(self, hostname, hostgroups):
        return self.webservice.call_clapi('sethostgroup', 'HOST', [hostname, "|".join(hostgroups)])

    def deletehostgroup(self, hostname, hostgroups):
        return self.webservice.call_clapi('delhostgroup', 'HOST', [hostname, "|".join(hostgroups)])

    def setseverity(self, hostname, name):
        return self.webservice.call_clapi('setseverity', 'HOST', [hostname, name    ])

    def unsetseverity(self, hostname):
        return self.webservice.call_clapi('unsetseverity', 'HOST', hostname)

    def enable(self, hostname):
        return self.webservice.call_clapi('enable', 'HOST', hostname)

    def disable(self, hostname):
        return self.webservice.call_clapi('disable', 'HOST', hostname)
