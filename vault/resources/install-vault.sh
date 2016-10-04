#!/usr/bin/env bash

set -e
set -x

if [[ -n $1 ]]; then
    echo "HEY $1"
    url="https://releases.hashicorp.com/vault/${1}/vault_${1}_linux_amd64.zip"
else
    echo "Need a version"
    exit 1
fi

if ! [ -x "$(command -v vault)" ]; then
    curl $url > vault.zip
    unzip vault.zip
    rm vault.zip
fi


