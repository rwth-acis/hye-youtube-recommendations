#!/usr/bin/env bash

set -e

# print all comands to console if DEBUG is set
if [[ ! -z "${DEBUG}" ]]; then
    set -x
fi

NODE_ID_SEED=${NODE_ID_SEED:-$RANDOM}

# set some helpful variables
export SERVICE_PROPERTY_FILE='etc/i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations.properties'
export WEB_CONNECTOR_PROPERTY_FILE='etc/i5.las2peer.connectors.webConnector.WebConnector.properties'
export SERVICE_VERSION=$(awk -F "=" '/service.version/ {print $2}' gradle.properties )
export SERVICE_NAME=$(awk -F "=" '/service.name/ {print $2}' gradle.properties )
export SERVICE_CLASS=$(awk -F "=" '/service.class/ {print $2}' gradle.properties )
export SERVICE=${SERVICE_NAME}.${SERVICE_CLASS}@${SERVICE_VERSION}

if [ -n "$CLIENT_ID" ]; then
    sed -i "s|clientId = ID|clientId = ${CLIENT_ID}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$CLIENT_SECRET" ]; then
    sed -i "s|clientSecret = SECRET|clientSecret = ${CLIENT_SECRET}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$API_KEY" ]; then
    sed -i "s|apiKey = KEY|apiKey = ${API_KEY}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$MY_SQL_HOST" ]; then
    sed -i "s|mysqlHost = localhost|mysqlHost = ${MY_SQL_HOST}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$MY_SQL_DATABASE" ]; then
    sed -i "s|mysqlDatabase = hye|mysqlDatabase = ${MY_SQL_DATABASE}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$MY_SQL_USER_NAME" ]; then
    sed -i "s|mysqlUser = newuser|mysqlUser = ${MY_SQL_USER_NAME}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$MY_SQL_USER_PW" ]; then
    sed -i "s|mysqlPassword = changeme|mysqlPassword = ${MY_SQL_USER_PW}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$ML_LIB_URL" ]; then
    sed -i "s|mlLibUrl = http://localhost:8000/|mlLibUrl = ${ML_LIB_URL}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$MODEL_NAME" ]; then
    sed -i "s|modelName = HyE-MatrixFactorization|modelName = ${MODEL_NAME}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$HYE_SERVICE_AGENT_NAME" ]; then
    sed -i "s|serviceAgentName = hyeAgent|serviceAgentName = ${HYE_SERVICE_AGENT_NAME}|" "${SERVICE_PROPERTY_FILE}"
fi
if [ -n "$HYE_SERVICE_AGENT_PW" ]; then
    sed -i "s|serviceAgentPw = changeMe|serviceAgentPw = ${HYE_SERVICE_AGENT_PW}|" "${SERVICE_PROPERTY_FILE}"
fi

# wait for any bootstrap host to be available
if [[ ! -z "${BOOTSTRAP}" ]]; then
    echo "Waiting for any bootstrap host to become available..."
    for host_port in ${BOOTSTRAP//,/ }; do
        arr_host_port=(${host_port//:/ })
        host=${arr_host_port[0]}
        port=${arr_host_port[1]}
        if { </dev/tcp/${host}/${port}; } 2>/dev/null; then
            echo "${host_port} is available. Continuing..."
            break
        fi
    done
fi

set -f
LAUNCH_COMMAND='java -cp lib/* --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED i5.las2peer.tools.L2pNodeLauncher -p '"${LAS2PEER_PORT} ${SERVICE_EXTRA_ARGS}"
if [[ ! -z "${BOOTSTRAP}" ]]; then
    LAUNCH_COMMAND="${LAUNCH_COMMAND} -b ${BOOTSTRAP}"
fi

# it's realistic for different nodes to use different accounts (i.e., to have
# different node operators). this function echos the N-th mnemonic if the
# variable WALLET is set to N. If not, first mnemonic is used
function selectMnemonic() {
    declare -a mnemonics=("differ employ cook sport clinic wedding melody column pave stuff oak price" "memory wrist half aunt shrug elbow upper anxiety maximum valve finish stay" "alert sword real code safe divorce firm detect donate cupboard forward other" "pair stem change april else stage resource accident will divert voyage lawn" "lamp elbow happy never cake very weird mix episode either chimney episode" "cool pioneer toe kiwi decline receive stamp write boy border check retire" "obvious lady prize shrimp taste position abstract promote market wink silver proof" "tired office manage bird scheme gorilla siren food abandon mansion field caution" "resemble cattle regret priority hen six century hungry rice grape patch family" "access crazy can job volume utility dial position shaft stadium soccer seven")
    if [[ ${WALLET} =~ ^[0-9]+$ && ${WALLET} -lt ${#mnemonics[@]} ]]; then
        # get N-th mnemonic
        echo "${mnemonics[${WALLET}]}"
    else
        # note: zsh and others use 1-based indexing. this requires bash
        echo "${mnemonics[0]}"

    fi
}

#prepare pastry properties
echo external_address = $(curl -s https://ipinfo.io/ip):${LAS2PEER_PORT} >etc/pastry.properties
echo ${LAUNCH_COMMAND}

echo external_address = $(curl -s https://ipinfo.io/ip):${LAS2PEER_PORT} > etc/pastry.properties
# start the service within a las2peer node
if [[ -z "${@}" ]]; then
    if [ -n "$LAS2PEER_ETH_HOST" ]; then
        exec ${LAUNCH_COMMAND} --observer --node-id-seed $NODE_ID_SEED --ethereum-mnemonic "$(selectMnemonic)" startService\("'""${SERVICE}""'"\) startWebConnector "node=getNodeAsEthereumNode()" "registry=node.getRegistryClient()" "n=getNodeAsEthereumNode()" "r=n.getRegistryClient()"
    else
        exec ${LAUNCH_COMMAND} --observer --node-id-seed $NODE_ID_SEED startService\("'""${SERVICE}""'"\)  startWebConnector
    fi
else
    exec ${LAUNCH_COMMAND} uploadStartupDirectory ${@}
fi
