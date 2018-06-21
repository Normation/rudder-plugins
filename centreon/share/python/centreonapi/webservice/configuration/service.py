# -*- coding: utf-8 -*-

from centreonapi.webservice import Webservice

class Service(object):
    """
    Centreon Web Service Object
    """

    def __init__(self):
        self.webservice = Webservice.getInstance()

    def list(self):
        return self.webservice.call_clapi('show', 'SERVICE')

    def add(self, hostname, servicename, template):
        values = [hostname, servicename, template]
        return self.webservice.call_clapi('add', 'SERVICE', values)

    def delete(self, hostname, servicename):
        return self.webservice.call_clapi('del', 'SERVICE', [hostname, servicename])

    def setparam(self, hostname, servicename, name, value):
        values = [hostname, servicename, name, value]
        return self.webservice.call_clapi('setparam', 'SERVICE', values)

    def addhost(self):
        pass

    def sethost(self):
        pass

    def delhost(self):
        pass

    def getmaro(self, hostname, servicename):
        return self.webservice.call_clapi('getmacro', 'SERVICE', [hostname,servicename])

    def setmacro(self, hostname, servicename, name, value, description):
        values = [hostname, servicename, name, value, description]
        return self.webservice.call_clapi('setmacro', 'SERVICE', values)

    def delmacro(self, hostname, servicename, name):
        values = [hostname, servicename, name ]
        return self.webservice.call_clapi('delmacro', 'SERVICE', values)

    def setseverity(self, hostname, servicename, name):
        values = [hostname, servicename, name ]
        return self.webservice.call_clapi('setseverity', 'SERVICE', values)

    def unsetseverity(self, hostname, servicename):
        values = [hostname, servicename]
        return self.webservice.call_clapi('unsetseverity', 'SERVICE', values)

    def getcontact(self, hostname, servicename):
        values = [hostname, servicename]
        return self.webservice.call_clapi('getcontact', 'SERVICE', values)

    def addcontact(self, hostname, servicename, contact):
        values = [hostname, servicename, contact]
        return self.webservice.call_clapi('addcontact', 'SERVICE', values)

    def setcontact(self, hostname, servicename, contact):
        values = [hostname, servicename, '|'.join(contact)]
        return self.webservice.call_clapi('setcontact', 'SERVICE', values)

    def delcontact(self, hostname, servicename, contact):
        try:
            for i in contact:
                values = [hostname, servicename, i]
                self.webservice.call_clapi('delcontact', 'SERVICE', values)
            return True
        except Exception:
            return False


    def getcontactgrup(self, hostname, servicename):
        values = [hostname, servicename]
        return self.webservice.call_clapi('getcontactgroup', 'SERVICE', values)

    def setcontactgroup(self, hostname, servicename, contact):
        values = [hostname, servicename, '|'.join(contact)]
        return self.webservice.call_clapi('setcontactgroup', 'SERVICE', values)

    def delcontactgroup(self, hostname, servicename, contact):
        try:
            for i in contact:
                values = [hostname, servicename, i]
                self.webservice.call_clapi('delcontactgroup', 'SERVICE', values)
            return True
        except Exception:
            return False

    def gettrap(self, hostname, servicename):
        values = [hostname, servicename]
        return self.webservice.call_clapi('gettrap', 'SERVICE', values)

    def addtrap(self, hostname, servicename, trap):
        values = [hostname, servicename, trap]
        return self.webservice.call_clapi('addtrap', 'SERVICE', values)

    def settrap(self, hostname, servicename, trap):
        values = [hostname, servicename, '|'.join(trap)]
        return self.webservice.call_clapi('settrap', 'SERVICE', values)

