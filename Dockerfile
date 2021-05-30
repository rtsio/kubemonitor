FROM debian:bullseye-slim

COPY service-account-private-key.json /

RUN apt-get update && \
    apt-get install -y apt-transport-https ca-certificates curl gnupg2 && \
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg |apt-key --keyring /usr/share/keyrings/cloud.google.gpg add - && \
    apt-get update && \
    apt-get -y install git google-cloud-sdk && \
    apt-get -y install build-essential python3 python3-venv libcurl4-openssl-dev libssl-dev python3-dev && \
    apt-get autoremove --purge -y && \
    apt-get -y clean all && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
    gcloud auth activate-service-account --key-file=/service-account-private-key.json && \
    rm -rf /service-account-private-key.json

COPY build/libs/*.jar /app.jar

ENTRYPOINT ["java","-jar","/app.jar"]