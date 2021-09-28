#!/bin/bash

add_component () {
  anchor_item=$1
  new_item=$2
  sed -i -e "s/^\(\(\s\+\)@component = \"${anchor_item}\"\)/\1\n\2@component = \"${new_item}\"/g" technique.rd
}

input="../item_list"
while IFS= read -r line
do
  item_name=$(echo $line | cut -d' ' -f4-)

  # Insert item number
  item_nb=$(echo $line | cut -d' '  -f1)
  add_component "${item_name}" $item_nb

  # Insert item server level
  item_raw_server=$(echo $line | cut -d' ' -f2)
  if [ "${item_raw_server}" != "0" ]; then
    add_component "${item_name}" "server_${item_raw_server}"
  fi

  item_raw_workstation=$(echo $line | cut -d' ' -f3)
  if [ "${item_raw_workstation}" != "0" ]; then
    add_component "${item_name}" "workstation_${item_raw_workstation}"
  fi
done < "$input"
