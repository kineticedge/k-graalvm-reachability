#!/bin/bash

set -ev

cd /opt/keycloak/bin

# login
./kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password keycloak

./kcadm.sh update realms/master -s accessTokenLifespan=60000

# create client scope
CLIENT_SCOPE_KAFKA_ACCESS_ID=$(./kcadm.sh create -x "client-scopes" -r master \
    -s name=kafka-access \
    -s protocol=openid-connect \
    -s type=default \
    -s 'attributes."include.in.token.scope"=true' \
    -s 'attributes."display.on.consent.screen"=false' \
    -s 'attributes."gui.order"=' \
    2>&1 | cut -d\' -f2)

./kcadm.sh update default-default-client-scopes/${CLIENT_SCOPE_KAFKA_ACCESS_ID}

# add an audience mapper to the client scope, use the ability to hard-code
./kcadm.sh create client-scopes/${CLIENT_SCOPE_KAFKA_ACCESS_ID}/protocol-mappers/models \
    -s name=kafka-audience \
    -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper \
    -s 'config."access.token.claim"=true' \
    -s 'config."multivalued"=true' \
    -s 'config."included.custom.audience"=kafka'

./kcadm.sh create clients -r master \
	-s clientId=app-ui \
	-s enabled=true \
	-s clientAuthenticatorType=client-secret \
	-s secret=app-ui-secret \
	-s serviceAccountsEnabled=true \
  -s defaultClientScopes="[\"kafka-access\"]"

./kcadm.sh get clients -r master --fields 'clientId'
