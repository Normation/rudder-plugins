# -*- coding: utf-8 -*-

from centreonapi.webservice import Webservice

class Hostgroups(object):

    def __init__(self):
        """
        Constructor
        """
        self.webservice = Webservice.getInstance()

    def list(self):
        """
        Get HostGroups list
        """
        return self.webservice.call_clapi('show', 'HG')

    def add(self, name, alias):
        values = [ name, alias]
        return self.webservice.call_clapi('add', 'HG', values)

    def delete(self, name):
        return self.webservice.call_clapi('del', 'HG', name)


