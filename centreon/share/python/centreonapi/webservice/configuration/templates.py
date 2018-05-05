# -*- coding: utf-8 -*-

from centreonapi.webservice import Webservice

class Templates(object):

    def __init__(self):
        """
        Constructor
        """
        self.webservice = Webservice.getInstance()

    def list(self):
        """
        Get host template list
        """
        return self.webservice.call_clapi('show', 'HTPL')

