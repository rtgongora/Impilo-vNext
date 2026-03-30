#!/bin/bash

# =============================================================================
# Impilo vNext — Local Development Certificate Generator (mTLS)
# =============================================================================
# Generates a self-signed CA and certificates for Envoy and services.
# Used for testing mTLS in local development environments.
# =============================================================================

set -e

CERT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$CERT_DIR"

echo "Generating certificates in $CERT_DIR..."

# 1. Create Certificate Authority (CA)
if [ ! -f ca.key ]; then
    echo "Creating CA..."
    openssl genrsa -out ca.key 4096
    openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt \
        -subj "/C=ZW/ST=Harare/L=Harare/O=Impilo/CN=Impilo-Dev-CA"
fi

# 2. Create Envoy Server Certificate
if [ ! -f envoy.key ]; then
    echo "Creating Envoy certificate..."
    openssl genrsa -out envoy.key 2048
    openssl req -new -key envoy.key -out envoy.csr \
        -subj "/C=ZW/ST=Harare/L=Harare/O=Impilo/CN=envoy"
    
    cat > envoy.ext <<EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = envoy
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

    openssl x509 -req -in envoy.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
        -out envoy.crt -days 365 -sha256 -extfile envoy.ext
    rm envoy.csr envoy.ext
fi

# 3. Create Service Client Certificate (for mTLS)
if [ ! -f service.key ]; then
    echo "Creating Service client certificate..."
    openssl genrsa -out service.key 2048
    openssl req -new -key service.key -out service.csr \
        -subj "/C=ZW/ST=Harare/L=Harare/O=Impilo/CN=impilo-service"
    
    cat > service.ext <<EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = *.impilo.local
DNS.2 = localhost
EOF

    openssl x509 -req -in service.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
        -out service.crt -days 365 -sha256 -extfile service.ext
    rm service.csr service.ext
fi

# 4. Create OPA Sidecar Certificate (optional, but good for completeness)
if [ ! -f opa.key ]; then
    echo "Creating OPA certificate..."
    openssl genrsa -out opa.key 2048
    openssl req -new -key opa.key -out opa.csr \
        -subj "/C=ZW/ST=Harare/L=Harare/O=Impilo/CN=opa"
    
    cat > opa.ext <<EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = opa
DNS.2 = localhost
EOF

    openssl x509 -req -in opa.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
        -out opa.crt -days 365 -sha256 -extfile opa.ext
    rm opa.csr opa.ext
fi

chmod 644 *.crt *.key
echo "All certificates generated successfully."
