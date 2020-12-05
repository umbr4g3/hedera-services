#!/bin/bash

# script to be called by platform regression scrip to transfer files to update Node
#
#
set -eE
UPDATE_NODE_IP_ADDRESS=""
NODE0_IP_ADDRESS=""
CONFIG_FILE_PATH=""
copyFilesToUpdateNode() {
  # copy config.txt , File0.0.150 and saved state to update nodes to start from the state
  set -x
  scp -o StrictHostKeyChecking=no -i services-regression.pem -p $CONFIG_FILE_PATH ubuntu@$UPDATE_NODE_IP_ADDRESS:/home/ubuntu/remoteExperiment >> shell.log 2>&1
  echo "Copying config.txt to the update Node" >> shell.log 2>&1
  ssh -t -t -o StrictHostKeyChecking=no  -i services-regression.pem  ubuntu@$UPDATE_NODE_IP_ADDRESS "mkdir -p  /home/ubuntu/remoteExperiment/data/diskFs/0.0.7" >> shell.log 2>&1
  scp -o StrictHostKeyChecking=no -i services-regression.pem -r -p data/diskFs/0.0.3/* ubuntu@$UPDATE_NODE_IP_ADDRESS:/home/ubuntu/remoteExperiment/data/diskFs/0.0.7 >> shell.log 2>&1
  echo "Copying File0.0.150 to the update Node" >> shell.log 2>&1
  ssh -t -t -o StrictHostKeyChecking=no  -i services-regression.pem  ubuntu@$UPDATE_NODE_IP_ADDRESS "mkdir -p  /home/ubuntu/remoteExperiment/data/saved/com.hedera.services.ServicesMain/4/" >> shell.log 2>&1
  scp -o StrictHostKeyChecking=no -i services-regression.pem -r -p data/saved/com.hedera.services.ServicesMain/0/123/ \
    ubuntu@$UPDATE_NODE_IP_ADDRESS:~/remoteExperiment/data/saved/com.hedera.services.ServicesMain/4/  >> shell.log 2>&1
  echo "Copying saved state files to the update Node"  >> shell.log 2>&1
}

parseNewConfig() {
  # parse config.txt to get the node 0 IP address and update node Ip address
  CONFIG_FILE_PATH="../HapiApp2.0/config.txt"
  mapfile -t addressLinesArray < <(grep -Po '^address.*$' $CONFIG_FILE_PATH)
  NODE0_IP_ADDRESS=$(cut -d',' -f7 <<<${addressLinesArray[0]})
  UPDATE_NODE_IP_ADDRESS=$(cut -d',' -f7 <<<${addressLinesArray[${#addressLinesArray[@]} - 1]})
  UPDATE_NODE_IP_ADDRESS=`echo $UPDATE_NODE_IP_ADDRESS`
  echo "Update Node IpAddress $UPDATE_NODE_IP_ADDRESS and Address of Node0 $NODE0_IP_ADDRESS" >> shell.log 2>&1
}

parseNewConfig
copyFilesToUpdateNode