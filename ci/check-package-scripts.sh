#!/bin/bash

errors=0

for filename in preinst postinst prerm postrm; do
    found_files=$(find . -type f -name "$filename" 2>/dev/null)
    
    if [ -n "$found_files" ]; then
        while IFS= read -r file; do
            if [ ! -x "$file" ]; then
                echo "${file} is not executable"
                errors=1
            fi
        done <<< "$found_files"
    fi
done

if [ ${errors} = 0 ];
then
  echo "Plugin scripts are executable"
else
  echo "Some plugin scripts are not executable"
fi
exit ${errors}
