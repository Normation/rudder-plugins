#!/bin/bash
TECHNIQUE_FILE=$1
ITEM_LIST=$2

add_component () {
  anchor_item=$1
  new_item=$2
  target=$3
  escaped_anchor_item=$(printf '%s\n' "${anchor_item}" | sed -e 's/[\/&]/\\&/g')
  escaped_new_item=$(printf '%s\n' "${new_item}" | sed -e 's/[\/&]/\\&/g')
  sed -i -e "s/^\(\(\s\+\)@component = \"${escaped_anchor_item}\"\)/\1\n\2@component = \"${escaped_new_item}\"/g" $3
}

while IFS= read -r line
do
  item_name=$(echo $line | cut -d' ' -f4-)

  # Insert item number
  item_nb=$(echo $line | cut -d' '  -f1)
  add_component "${item_name}" $item_nb $TECHNIQUE_FILE

  # Insert item server level
  item_raw_server=$(echo $line | cut -d' ' -f2)
  if [ "${item_raw_server}" != "0" ]; then
    add_component "${item_name}" "server_${item_raw_server}" $TECHNIQUE_FILE
  fi

  item_raw_workstation=$(echo $line | cut -d' ' -f3)
  if [ "${item_raw_workstation}" != "0" ]; then
    add_component "${item_name}" "workstation_${item_raw_workstation}" $TECHNIQUE_FILE
  fi
done < "$ITEM_LIST"
