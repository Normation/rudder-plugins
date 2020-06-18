#!/usr/bin/expect -f
 
set timeout -1
set USER [lindex $argv 0]
set BASTIONIP [lindex $argv 1]
set PASSWORD [lindex $argv 2]

spawn waapm seal -u $USER -b mybastion=$BASTIONIP
expect "Pass*"
send $PASSWORD
send "\r"
interact
