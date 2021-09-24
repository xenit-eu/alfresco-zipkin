#!/usr/bin/env bash

set -o pipefail

if [ "$#" -lt 2 ]; then
    echo "  Usage: $0 <hostname>[:<port>] <path> [expected-status-code]"
    echo "Example: $0 localhost:8080 /alfresco/"
    exit 0
fi

HOST=$(echo $1 | cut -d ':' -f 1)
PORT=$(echo ${1:-:80} | cut -d ':' -f 2)
PATH=${2}
EXPECTED=${3:-200}

# Make sure ${PATH} starts with a '/'
if  [[ $PATH != /* ]];
then
    PATH="/${PATH}"
fi

echo "GET http://${HOST}:${PORT}${PATH} - expecting HTTP ${EXPECTED}"

SC=$(echo -e "GET ${PATH} HTTP/1.0\r\n" | /bin/nc -v ${HOST} ${PORT} | /usr/bin/head -1 | /usr/bin/cut -d ' ' -f 2)

NC_RESULT=$?
if  [[ $NC_RESULT == 1 ]];
then
    echo "Connection error!"
    exit 1
fi

# At least partial success - we created a connection and got a reply ?!
echo -n "HTTP $SC - "

if [[ $SC != ${EXPECTED} ]];
then
    echo "but expected HTTP ${EXPECTED} - Failure!"
    exit 1
fi

echo "Success!"