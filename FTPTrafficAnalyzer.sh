#!/bin/sh -x
#echo $0
#BASEDIR=$(dirname $0)
#echo "Script location: ${BASEDIR}"
DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $DIR
#echo $DIR
#read -p 'Press Enter to close shell...' var
java -jar FTPTrafficAnalyzer-v1.00.jar "$1" $2
#read -p 'Press Enter to close shell...' var
