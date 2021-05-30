# kubemonitor
kubemonitor is a light utility for monitoring Kubernetes clusters. It is deployed as a stateless service and built using Spring Boot and 
the fabric8 kubernetes-client library. Similar to [Bitnami's kubewatch](https://github.com/bitnami-labs/kubewatch) but with more generic focus.

Developed for use with GKE, currently being adjusted to work with any Kubernetes deployment.

### Features
* Monitor Kubernetes clusters in GKE by comparing running StatefulSets and Deployments against a JSON config of
expected workloads; detect missing workloads or workloads where not all replicas are ready
* Parse Kubernetes Events and notify on unhealthy conditions such as failing liveness/readiness probes, OOMKills, etc.
* Detect new deployments and optionally notify about them if your CI/CD pipeline doesn't do it already
* Schedule automated workload scale down/scale up, useful for weekend maintenance windows
* Interface via HTTP/REST or scheduled jobs, and send Slack notifications

Prometheus can be used to monitor clusters, but doesn't work when the entire job to create the cluster has silently failed; it also uses a trend/metric based approach and can miss
things like single liveness probe failures. K8s Events contain important cluster information but only exist for an hour;
 even the [official documentation](https://kubernetes.io/docs/tasks/debug-application-cluster/events-stackdriver/) recommends
 installing a 3rd party solution. I couldn't find a good free one (Stackdriver on GCP takes forever to configure and suffers from the same
 slowness/trend based approach as Prometheus), so I built my own.

### Using kubemonitor for your GKE cluster
Currently kubemonitor is limited to GKE, but this will change in the near future. You will need to create a GCP service account and give it the proper permissions to access your cluster. 
This can be done with the following commands:

```
gcloud iam service-accounts create your-account-name
gcloud iam service-accounts keys create service-account-private-key.json \
    --iam-account=your-account-name@PROJECT_ID.iam.gserviceaccount.com
gcloud projects add-iam-policy-binding PROJECT_ID \
    --member=serviceAccount:your-account-name@PROJECT_ID.iam.gserviceaccount.com \
    --role=roles/container.developer
 ```
The service has been tested with the `container.developer` role, but this may give more access than you would like, so adjust 
accordingly - read-only access should be enough if you don't use the maintenance scheduling feature.
GCP service accounts can access multiple projects; see 
[this link](https://stackoverflow.com/questions/35479025/cross-project-management-using-service-account) if you're having trouble 
configuring cross-project access.
    
Once you've generated a private key, place it into the root directory as `service-account-private-key.json` before 
building the Docker container.

You will also need to adjust `monitoring-config.json` with your clusters.
After this you can call the `GET /status?project=your-project&cluster=your-cluster` endpoint to check that everything works.
Currently the service is completely stateless and has no other dependencies.

Read more about service account authentication here:

* [Authenticating to the Kubernetes API server](https://cloud.google.com/kubernetes-engine/docs/how-to/api-server-authentication)

### Configuration

Example configuration:

```json
{
  "clusters": [
    {
      "project": "my-gcp-project",
      "name": "my-gke-cluster",
      "zone": "europe-west4-b",
      "enabled": true,
      "events": {
        "enabled": true,
        "types": {
          "readiness-probe": false,
          "liveness-probe": true,
          "oom-kill": true
        }
      },
      "notifications": {
        "events": {
          "enabled": true,
          "slack-webhook": "my-channel"
        },
        "deployments": {
          "enabled": false
        },
        "maintenance": {
          "enabled": false
        }
      },
      "expected-workloads": {
        "deployments": [
          "example-service-1",
          "example-service-2"
        ],
        "stateful-sets": [
          "example-service-3",
          "example-service-4"
        ]
      }
    }
  ],
  "slack-webhooks": [
    {
      "name": "my-channel",
      "url": "https://hooks.slack.com/services/.."
    }
  ]
}
```

* `clusters` - controls which clusters are visible to the tool and can be queried via HTTP for status, monitored for events, 
used for maintenance requests, etc.

    * `cluster.project` - GCP project name.
    * `cluster.name` - GKE cluster name.
    * `cluster.zone` - GKE cluster zone.
    * `cluster.enabled` - if this is set to false, you can leave the cluster in the configuration, but it will be ignored entirely.
    * `cluster.events` - event configuration: 
        * `events.enabled` - if this cluster should be watched for k8s Events. Events are used to detect expected 
        deployments of new code, so turning this off might result in some false positives from monitoring.
        * `events.types` - configure which types of events are alerted on or ignored.
            * `types.readiness-probe` - alert on readiness probe failures. Container kills due to probe failure will
            always be alerted on if events are enabled.
            * `types.liveness-probe` - alert on liveness probe failures. Container kills due to probe failure will
            always be alerted on if events are enabled.
            * `types.oom-kill` - alert on out of memory kill events ("OOMKilling").                        
    * `cluster.notifications` - notification configuration:
        * `notifications.events` - send notifications for k8s Events.
        * `notifications.deployments` - send notifications for detected deployments.
        * `notifications.maintenance` - send notifications for maintenance.
    * `cluster.expected-workloads` - which workloads to "expect" when checking the cluster.
        * `expected-workloads.deployments` - array of Deployment names.
        * `expected-workloads.stateful-sets` - array of StatefulSet names.
        
`slack-webhooks` - webhook objects that can be re-used through-out the configuration, to avoid pasting URLs everywhere.


### API
##### Cluster status
`GET /status?project=<project>&cluster=<cluster>` - get cluster status. Cluster must be in monitoring config.

Response:
```json
{
    "project": "my-project",
    "cluster": "my-cluster",
    "state": "OK",
    "issues": [],
    "deploymentsActive": []
}
```

Possible states:
* `OK` - everything is fine
* `DEGRADED` - some things are fine, but others are not
* `DOWN` - the entire cluster appears to be missing

`issues` - list of strings describing detected issues, such as missing or not fully healthy workloads.

`deploymentsActive` - deployments of new code detected via k8s Events - these will cancel out a missing or unhealthy workload.

Example of an unhealthy cluster status:
```json
{
    "project": "my-project",
    "cluster": "my-cluster",
    "state": "DEGRADED",
    "issues": [
        "StatefulSet example-service-3 expected 1 replicas, but only 0 ready"
    ],
    "deploymentsActive": []
}
```

##### Create maintenance task
`POST /maintenance` - create maintenance task that will scale down and then scale up the selected workloads. Cluster must be in monitoring config.

Payload:
```json
{
    "project": "my-project",
    "cluster": "my-cluster",
    "startTime": "2020-10-23T14:42:11.355982Z",
    "endTime": "2020-10-23T18:00:00Z",
    "workloadsToScale": [
        "example-service-1",
        "example-service-2",
        "example-service-3"
    ]
}
```

Note that the service is currently stateless and these are not persisted if restarted.


##### Get current time
`GET /now` - get current time in ISO timestamp (useful for creating maintenance payload).

##### Get the current configuration
`GET /config` - return current cluster config.

### Building & deployment
To build:

`./gradlew clean build`

### Contributing
Pull requests are welcome and encouraged. 

### TODO
* Strip out GKE/GCP specific code
* Explore converting to a public Helm chart, similar to Bitnami's kubewatch
* More reliable detection of deployments, especially for long-starting services
* Capture other unhealthy events - see [this link](https://www.bluematador.com/blog/kubernetes-events-explained)
* Monitor other workload types - DaemonSets, etc.
* Add stateful storage for maintenance requests, etc.
* Move to [k8s informer instead of watch](https://rohaan.medium.com/introduction-to-fabric8-kubernetes-java-client-informer-api-b945082d69af)



