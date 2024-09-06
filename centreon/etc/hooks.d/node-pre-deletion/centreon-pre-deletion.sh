#!/bin/sh

# Hooks parameter are passed by environment variable: 
#
# - RUDDER_NODE_ID              : the nodeId
# - RUDDER_NODE_HOSTNAME        : the node fully qualified hostname
# - RUDDER_NODE_POLICY_SERVER_ID: the node policy server id
# - RUDDER_AGENT_TYPE           : agent type ("cfengine-nova" or "cfengine-community")

 
# Errors code on hooks are interpreted as follow:
# - 0     : success, no log (apart if debug one)          , continue to next hook
# - 1-31  : error  , error   log in /var/log/rudder/webapp/, stop processing
# - 32-63 : warning, warning log in /var/log/rudder/webapp/, continue to next hook
# - 64-255: reserved for future use case. Behavior may change without notice. 

/opt/rudder/bin/centreon-plugin hook rm $RUDDER_NODE_ID

exit 0
