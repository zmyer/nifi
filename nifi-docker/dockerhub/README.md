<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Docker Image Quickstart

## Capabilities
This image currently supports running in standalone mode either unsecured or with user authentication provided through:
   * [Two-Way SSL with Client Certificates](http://nifi.apache.org/docs/nifi-docs/html/administration-guide.html#security-configuration)
   * [Lightweight Directory Access Protocol (LDAP)](http://nifi.apache.org/docs/nifi-docs/html/administration-guide.html#ldap_login_identity_provider)
   
## Building
The Docker image can be built using the following command:

    docker build -t apache/nifi:latest .

This build will result in an image tagged apache/nifi:latest

    # user @ puter in ~/Development/code/apache/nifi/nifi-docker/dockerhub
    $ docker images
    REPOSITORY               TAG                 IMAGE ID            CREATED                 SIZE
    apache/nifi              latest              f0f564eed149        A long, long time ago   1.62GB

**Note**: The default version of NiFi specified by the Dockerfile is typically that of one that is unreleased if working from source.
To build an image for a prior released version, one can override the `NIFI_VERSION` build-arg with the following command:
    
    docker build --build-arg=NIFI_VERSION={Desired NiFi Version} -t apache/nifi:latest .

There is, however, no guarantee that older versions will work as properties have changed and evolved with subsequent releases.
The configuration scripts are suitable for at least 1.4.0+.

## Running a container

### Standalone Instance, Unsecured
The minimum to run a NiFi instance is as follows:

    docker run --name nifi \
      -p 18080:8080 \
      -d \
      apache/nifi:latest
      
This will provide a running instance, exposing the instance UI to the host system on at port 18080,
viewable at `http://localhost:18080/nifi`.
        
### Standalone Instance, Two-Way SSL
In this configuration, the user will need to provide certificates and the associated configuration information.
Of particular note, is the `AUTH` environment variable which is set to `tls`.  Additionally, the user must provide an
the DN as provided by an accessing client certificate in the `INITIAL_ADMIN_IDENTITY` environment variable.
This value will be used to seed the instance with an initial user with administrative privileges.
Finally, this command makes use of a volume to provide certificates on the host system to the container instance.

    docker run --name nifi \
      -v /User/dreynolds/certs/localhost:/opt/certs \
      -p 18443:8443 \
      -e AUTH=tls \
      -e KEYSTORE_PATH=/opt/certs/keystore.jks \
      -e KEYSTORE_TYPE=JKS \
      -e KEYSTORE_PASSWORD=QKZv1hSWAFQYZ+WU1jjF5ank+l4igeOfQRp+OSbkkrs \
      -e TRUSTSTORE_PATH=/opt/certs/truststore.jks \
      -e TRUSTSTORE_PASSWORD=rHkWR1gDNW3R9hgbeRsT3OM3Ue0zwGtQqcFKJD2EXWE \
      -e TRUSTSTORE_TYPE=JKS \
      -e INITIAL_ADMIN_IDENTITY='CN=Random User, O=Apache, OU=NiFi, C=US' \
      -d \
      apache/nifi:latest

### Standalone Instance, LDAP
In this configuration, the user will need to provide certificates and the associated configuration information.  Optionally,
if the LDAP provider of interest is operating in LDAPS or START_TLS modes, certificates will additionally be needed.
Of particular note, is the `AUTH` environment variable which is set to `ldap`.  Additionally, the user must provide a
DN as provided by the configured LDAP server in the `INITIAL_ADMIN_IDENTITY` environment variable. This value will be 
used to seed the instance with an initial user with administrative privileges.  Finally, this command makes use of a 
volume to provide certificates on the host system to the container instance.

#### For a minimal, connection to an LDAP server using SIMPLE authentication:

    docker run --name nifi \
      -v /User/dreynolds/certs/localhost:/opt/certs \
      -p 18443:8443 \
      -e AUTH=tls \
      -e KEYSTORE_PATH=/opt/certs/keystore.jks \
      -e KEYSTORE_TYPE=JKS \
      -e KEYSTORE_PASSWORD=QKZv1hSWAFQYZ+WU1jjF5ank+l4igeOfQRp+OSbkkrs \
      -e TRUSTSTORE_PATH=/opt/certs/truststore.jks \
      -e TRUSTSTORE_PASSWORD=rHkWR1gDNW3R9hgbeRsT3OM3Ue0zwGtQqcFKJD2EXWE \
      -e TRUSTSTORE_TYPE=JKS \
      -e INITIAL_ADMIN_IDENTITY='cn=admin,dc=example,dc=org' \
      -e LDAP_AUTHENTICATION_STRATEGY='SIMPLE' \
      -e LDAP_MANAGER_DN='cn=admin,dc=example,dc=org' \
      -e LDAP_MANAGER_PASSWORD='password' \
      -e LDAP_USER_SEARCH_BASE='dc=example,dc=org' \
      -e LDAP_USER_SEARCH_FILTER='cn={0}' \
      -e LDAP_IDENTITY_STRATEGY='USE_DN' \
      -e LDAP_URL='ldap://ldap:389' \
      -d \
      apache/nifi:latest

#### The following, optional environment variables may be added to the above command when connecting to a secure  LDAP server configured with START_TLS or LDAPS

    -e LDAP_TLS_KEYSTORE: ''
    -e LDAP_TLS_KEYSTORE_PASSWORD: ''
    -e LDAP_TLS_KEYSTORE_TYPE: ''
    -e LDAP_TLS_TRUSTSTORE: ''
    -e LDAP_TLS_TRUSTSTORE_PASSWORD: ''
    -e LDAP_TLS_TRUSTSTORE_TYPE: ''

## Configuration Information
The following ports are specified by the Docker container for NiFi operation within the container and 
can be published to the host.

| Function                 | Property                      | Port  |
|--------------------------|-------------------------------|-------|
| HTTP Port                | nifi.web.http.port            | 8080  |
| HTTPS Port               | nifi.web.https.port           | 8443  |
| Remote Input Socket Port | nifi.remote.input.socket.port | 10000 |
