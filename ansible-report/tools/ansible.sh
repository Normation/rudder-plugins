#!/bin/bash

PLAYBOOK="$1"
PID="$2"
DIRECTIVE_ID="$3"

ansible-playbook $PLAYBOOK > /var/rudder/tmp/ansible_report_${PID}_${DIRECTIVE_ID} 2>/dev/null
if grep -qE "failed=[1-9][0-9]*" /var/rudder/tmp/ansible_report_${PID}_${DIRECTIVE_ID}; then
    exit 1
elif grep -qE "changed=[1-9][0-9]*" /var/rudder/tmp/ansible_report_${PID}_${DIRECTIVE_ID}; then
    exit 2
fi
exit 0
