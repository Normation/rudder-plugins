import requests, urllib3, json, configparser, log_tail, os, re, time

class NotifyWorker:

    def __init__(self, pipefile, conf):
        self.fifo_pipe = pipefile
        self.conf = conf
        self.notif_queue = []
        self.timestamp = int(time.time())
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
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
    
    def handle_non_compliance(self, msg_str):
        msg = Message(msg_str)
        self.notif_queue.append(msg)
        if self.conf["MAIL"]["on"] == "true":
            self.notify_mail()
        if self.conf["SLACK"]["on"] == "true":
            self.notify_slack(msg)

    def notify_slack(self, msg):
        for webhook in self.conf["SLACK"]["webhooks"].split(' '):
            requests.post(webhook, headers={"Content-type":"application/json"}, data=json.dumps({
                "text":"*RUDDER NON-COMPLIANCE*\n" + str(msg)
                }))

    def notify_mail(self):
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
