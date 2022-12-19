#!/bin/bash
# Create CA cert
openssl genrsa -out ca-key.pem 2048 
openssl req -new -x509 -nodes -days 100000 -key ca-key.pem -out ca-cert.pem -batch -subj '/CN=test-ca.example.com/C=FI'

# Server
openssl req -newkey rsa:2048 -nodes -keyout server-key.pem -out server-req.pem -batch -subj '/CN=test-server.example.com/C=FI'
openssl x509 -req -days 10000 -in server-req.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem
rm -f server-req.pem

# Client
openssl req -newkey rsa:2048 -days 10000 -nodes -keyout client-key.pem -out client-req.pem -batch -subj '/CN=test-client.example.com/C=FI'
openssl x509 -req -days 10000 -in client-req.pem -CA ca-cert.pem -CAkey ca-key.pem -CAserial ca-cert.srl -out client-cert.pem
rm -f client-req.pem

# keystore: import ca.crt, use password "changeit"
#/usr/lib/jvm/java/bin/keytool -storepass changeit -import -trustcacerts -alias root -file ca-cert.pem -keystore keystore.jks --noprompt

# keystore: combine pems
openssl pkcs12 -export -out combined.pfx -inkey server-key.pem -in server-cert.pem -passout pass:changeit

# keystore: import combined pems
/usr/lib/jvm/java/bin/keytool -importkeystore -srckeystore combined.pfx -srcstoretype PKCS12 -srcstorepass changeit -deststorepass changeit -destkeypass changeit -destkeystore keystore.jks
rm -f combined.pfx
