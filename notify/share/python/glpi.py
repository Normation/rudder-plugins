import requests, json

# TODO clean initializing to create the adequate ticket

# Class used to create the php filter to look for objects in the glpi api
class criterias:
  def __init__(self):
    self.array = []

  def addCriteria(self, criteria):
    self.array.append(criteria)

  def __str__(self):
    output = ""
    for iCriteria in range(len(self.array)):
      output += (self.array[iCriteria]).show(iCriteria) + "&"
    return output[:len(output)-1]
      
class criteria:
  def __init__(self, field, searchType, value, link="AND"):
    self.link = link
    self.field = field
    self.searchtype = searchType
    self.value = value

  def show(self, i):
    sep = "criteria[" + str(i) + "]"
    return sep + "[link]=" + self.link + "&" + sep + "[field]=" + self.field + "&" + sep + "[searchtype]=" + self.searchtype + "&" + sep + "[value]=" + self.value
    


# Class used to create a ticket in the glpi api
class glpiTicket:
  def __init__(self, name, content, status=1, urgency=3, impact=3, priority=3):
    self.name = name
    self.content = content
    self.status = status
    self.urgency = urgency
    self.impact = impact
    self.priority = priority
#    self.entities_id = entities_id
#    self.requesttypes_id = requesttypes_id
#    self.itilcategories_id = itilcategories_id 
#    self.type = type
#    self.global_validation = global_validation 

  def build(self):
    return { key:value for key, value in self.__dict__.items() if not key.startswith('__') and not callable(key) }

# Class used to create and parse the api queries
class glpiSession:
  def __init__(self, userToken, apiToken, baseUrl):
    self.userToken = userToken
    self.apiToken  = apiToken
    self.baseUrl   = baseUrl
    self.headers   = ''
    self.sessionToken = ''
    self.field = {
      "content": "21",
       "status": "12",
         "name": "1",
         "type": "14"
      }
    self.status = {
      "assigned": "2",
       "planned": "3",
       "pending": "4",
        "solved": "5",
        "closed": "6",
           "new": "1"
      }

  def initSession(self):
    headers = { 'Content-Type': 'application/json', 'Authorization': "user_token "+self.userToken, 'App-Token': self.apiToken }
    response = requests.get(self.baseUrl + '/initSession', headers=headers)
    if (self.controlCode(['200'], response)):
      self.sessionToken = response.json()['session_token']
      self.headers = { 'Content-Type': 'application/json', 'Session-Token': self.sessionToken, 'App-Token': self.apiToken }

  def killSession(self):
    response = requests.get(self.baseUrl + '/killSession', headers=self.headers)
    exit()

  def openTicket(self, name, description):
    data = { "input": glpiTicket(name, description).build() }
    response = requests.post(self.baseUrl + '/Ticket' , headers=self.headers, data=json.dumps(data))
    if (self.controlCode(['201'], response)):
      return 0

  def getTickets(self):
    response = requests.get(self.baseUrl + '/Ticket' , headers=self.headers)
    if (self.controlCode(['200'], response)):
      return response.json()

  def getTicket(self, ticketID):
    response = requests.get(self.baseUrl + '/Ticket/' + ticketID , headers=self.headers)
    if (self.controlCode(['200'], response)):
      return response.json()

  def searchTicket(self, name, content=None, ticket_status=None):
    filters = criterias()
    # Filter on Name
    nameCriteria = criteria(self.field['name'], "contains", name, link="AND")
    filters.addCriteria(nameCriteria)
    # Filter on Description
    if (content != None):
      contentCriteria = criteria(self.field['content'], "contains", content, link="AND")
      filters.addCriteria(contentCriteria)
    # Filter on ticket_status
    if (ticket_status != None):
      statusCriteria = criteria(self.field['status'], "equals", self.status[ticket_status], link="AND")
      filters.addCriteria(statusCriteria)

    response = requests.get(self.baseUrl + '/search/Ticket?' + str(filters), headers=self.headers)
    if (self.controlCode(['200'], response)):
      return response.json()

  def similarTicketExists(self, ticket, ticket_status=None):
    if (ticket_status != None):
      for iStatus in ticket_status:
        if (self.searchTicket(ticket.name, ticket.content, iStatus)['totalcount'] >= 1):
          return True
      return False
    else:
      if (self.searchTicket(ticket.name, ticket.content, ticket_status)['totalcount'] >= 1):
        return True
      else:
        return False

  def customGETRequest(self, url):
    response = requests.get(self.baseUrl + url , headers=self.headers)
    if (self.controlCode(['200'], response)):
      return (response.text.encode('utf-8'))
    
  def controlCode(self, expectedCodes, response):
    if (str(response.status_code) in expectedCodes):
      return True
    else:
      print(response.text.encode('utf-8'))
      print("Something went wrong while calling the GLPI API, received error code " + str(response.status_code))
      self.killSession()
      return False

