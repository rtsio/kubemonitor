#!/bin/bash
./gradlew clean build && docker build -t kube-monitor . && docker run -p 8080:8080 -t kube-monitor
