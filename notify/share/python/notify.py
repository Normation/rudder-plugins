import requests, urllib3, json, configparser, log_tail, os, re, time, glpi, traceback, logging
logger = logging.getLogger(__name__)

class NotifyWorker:

    def __init__(self, pipefile, conf):
        self.fifo_pipe = pipefile
        self.conf = conf
        self.notif_queue = []
        self.timestamp = int(time.time())
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.glpi = None
        self.nodeFilter = None
        self.ruleFilter = None
        self.directiveFilter = None
        try:
            os.mkfifo(self.fifo_pipe)
        except OSError as e:
            if str(e).find("File exists") < 0:
                raise
    
    # This function reads non-compliance data from a pipe.
    # It is meant to be used with the Rudder server non-compliance hooks.
    def run(self):
        while True:
            if self.timestamp % 60 == 0: # This will check if there are unsent mails in the queue, and send them if the batch period is elapsed
                self.notify_mail()
            with open(self.fifo_pipe) as pipe:
                self.handle_non_compliance(pipe.read())

    # This function reads its input from the Rudder server non-compliance logs directly.
    # It is a temporary solution, used until the server's non-compliance hooks are implemented.
    def run_with_logtail(self):
        for line in log_tail.log_tail("/var/log/rudder/compliance/non-compliant-reports.log"):
            self.handle_non_compliance(line)

    def start(self):
        #self.run()
        self.run_with_logtail()

    def getFilters(self, filterName):
      try:
        filters = (self.conf["FILTERS"][filterName]).split(',')
        if isinstance(filters, list):
          if all('' == filt or filt.isspace() for filt in filters):
            return None
          else:
            return filters
        return None
      except:
        return None
  
    
    def handle_non_compliance(self, msg_str):
      try:
        self.nodeFilter = self.getFilters("nodeFilter")
        self.ruleFilter = self.getFilters("ruleFilter")
        self.directiveFilter = self.getFilters("directiveFilter")

        msg = Message(msg_str)

        logging.info("parsing line: " + msg_str)
        if self.applyGlobalFilters(msg) == True:
          if self.conf["MAIL"]["on"] == "true":
            self.notif_queue.append(msg)
            self.notify_mail()
          if self.conf["SLACK"]["on"] == "true":
            self.notify_slack(msg)
          if self.conf["GLPI"]["on"] == "true":
            self.notify_glpi(msg)
      except Exception as e:
        logging.error("Something went wrong while parsing the ticket:" + str(e))


    def notify_slack(self, msg):
      logging.info(" -- notify via slack --")
      for webhook in self.conf["SLACK"]["webhooks"].split(' '):
        requests.post(webhook, headers={"Content-type":"application/json"}, data=json.dumps({
            "text":"*RUDDER NON-COMPLIANCE*\n" + str(msg)
            }))

    def notify_glpi(self, msg):
       logging.info(" -- notify via glpi --")
       if (self.glpi == None):
         newSession = glpi.glpiSession(self.conf["GLPI"]["userToken"], self.conf["GLPI"]["apiToken"], self.conf["GLPI"]["url"])
         newSession.initSession()
         self.glpi = newSession

       # Do not parse repaired reports or logs
       if (msg.getResultStatus() != "result_repaired" and msg.getResultStatus() != "log_repaired" and msg.getResultStatus() != "log_warn"):
         ticketName = "[Rudder]" + msg.data['directive_name']
         ticketContent = str(msg)
         lookedContent = msg.withoutTimeStamp()
         lookedStatus = ['new', 'assigned', 'planned', 'pending']

         logging.info("looking for ticket: " + ticketName + "\n with content" + lookedContent)
         sampleTicket = glpi.glpiTicket(ticketName, lookedContent)
         if not self.glpi.similarTicketExists(sampleTicket, lookedStatus):
           logging.info(" -- creating ticket --")
           self.glpi.openTicket(ticketName, ticketContent)
           logging.info(" -- done --\n\n")
         else:
           logging.info(" -- SKIPPING, ticket already exists --")
       else:
         logging.info(" -- SKIPPING, repaired report --")

    def notify_mail(self):
        logging.info(" -- notify via email --")
        if self.conf["MAIL"]["nospam"] == "true" and time.time() - self.timestamp < self.get_mail_batch_period():
            return
            # The code above is enabling batch mail-sending by checking if the configured period has elapsed since last mail
        
        tmp_mailfile = "/tmp/rudder-notify-mail.txt"
        with open(tmp_mailfile, 'a') as out:
            out.write(str(len(self.notif_queue)) + " notification" + ('s' if len(self.notif_queue) > 1 else '') + " from Rudder :\n")
            for i in range(0, len(self.notif_queue)):
                out.write("# " + str(i+1) + ' :\n' + str(self.notif_queue[i]) + '\n')
        self.notif_queue = []
        for recipient in self.conf["MAIL"]["recipients"].split(' '):
            os.system("mail -s 'Rudder non-compliance notification' " + recipient + " < " + tmp_mailfile)
        os.remove(tmp_mailfile)
        self.timestamp = time.time() # And we reset the timestamp

    def get_mail_batch_period(self):
        regex = re.compile("([0-9]*)d?([0-9]*)h?([0-9]*)m?")
        g = map(int, regex.search(self.conf["MAIL"]["batch_period"]).groups())
        return 86400 * g[0] + 3600 * g[1] + 60 * g[2]

    def filterByNode(self, msg):
        if isinstance(self.nodeFilter, list):
          if msg.data['node_uuid'] in self.nodeFilter:
            return True
        return False
      
    def filterByDirective(self, msg):
        if isinstance(self.directiveFilter, list):
          if msg.data['directive_uuid'] in self.directiveFilter:
            return True
        return False
      
    def filterByRule(self, msg):
        if isinstance(self.ruleFilter, list):
          if msg.data['rule_uuid'] in self.ruleFilter:
            return True
        return False

    def applyGlobalFilters(self, msg):
      if self.filterByNode(msg) == True:
        logging.info(" -- Node " + msg.data['node_uuid'] + " in the whitelist --")
        return True
      elif self.filterByDirective(msg) == True:
        logging.info(" -- Directive " + msg.data['directive_uuid'] + " in the whitelist --")
        return True
      elif self.filterByRule(msg) == True:
        logging.info(" -- Rule " + msg.data['rule_uuid'] + " in the whitelist --")
        return True
      elif self.nodeFilter == None and self.directiveFilter == None and self.ruleFilter == None:
        logging.info(" -- No global filter set --\n\n")
        return True
      else:
        logging.info(" -- Skipping because not matching any global filter --")
        return False

class Message:

    def __init__(self, msg):
        regex = re.compile("^\[(?P<Date>[^\]]+)\] N: (?P<NodeUUID>[^ ]+) \[(?P<NodeFQDN>[^\]]+)\] S: \[(?P<Result>[^\]]+)\] R: (?P<RuleUUID>[^ ]+) \[(?P<RuleName>[^\]]+)\] D: (?P<DirectiveUUID>[^ ]+) \[(?P<DirectiveName>[^\]]+)\] T: (?P<TechniqueName>[^/]+)/(?P<TechniqueVersion>[^ ]+) C: \[(?P<ComponentName>[^\]]+)\] V: \[(?P<ComponentKey>[^\]]+)\] (?P<Message>.+)$")
        groups = regex.search(msg).groups()
        self.data = {
                    "date": groups[0],
                    "node_uuid": groups[1],
                    "node_fqdn": groups[2],
                    "result": groups[3],
                    "rule_uuid": groups[4],
                    "rule_name": groups[5],
                    "directive_uuid": groups[6],
                    "directive_name": groups[7],
                    "technique_name": groups[8],
                    "technique_version": groups[9],
                    "component_name": groups[10],
                    "component_key": groups[11],
                    "message": groups[12]
                }

    def __str__(self):
        return " - Date: " + self.data["date"] + \
            "\n - Node UUID: " + self.data["node_uuid"] + \
            "\n - Node FQDN: " + self.data["node_fqdn"] + \
            "\n - Result: " + self.data["result"] + \
            "\n - Rule UUID: " + self.data["rule_uuid"] + \
            "\n - Rule name: " + self.data["rule_name"] + \
            "\n - Directive UUID: " + self.data["directive_uuid"] + \
            "\n - Directive name: " + self.data["directive_name"] + \
            "\n - Technique name: " + self.data["technique_name"] + \
            "\n - Technique version: " + self.data["technique_version"] + \
            "\n - Component name: " + self.data["component_name"] + \
            "\n - Component key: " + self.data["component_key"] + \
            "\n - Message: " + self.data["message"] + "\n"

    def withoutTimeStamp(self):
      return self.__str__().split("\n")[1]

    def getResultStatus(self):
      return self.data['result']

