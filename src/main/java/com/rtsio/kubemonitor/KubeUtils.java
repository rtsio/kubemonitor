package com.rtsio.kubemonitor;

import com.rtsio.kubemonitor.exception.ClusterDoesNotExistException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class KubeUtils {

    /**
     * Call `gcloud container clusters get-credentials` to build kubeconfig for given cluster
     * Synchronized to prevent race conditions with different threads setting kubeconfig at once
     */
    public static synchronized void setClusterKubeconfig(String project, String zone, String cluster) throws ClusterDoesNotExistException {
        try {
            ProcessBuilder gcloudClusters = new ProcessBuilder("gcloud",
                    "container",
                    "clusters",
                    "get-credentials",
                    cluster,
                    "--zone",
                    zone,
                    "--project",
                    project).redirectErrorStream(true);
            Process gcloudClustersProcess = gcloudClusters.start();
            String output = new String(gcloudClustersProcess.getInputStream().readAllBytes());
            int exitCode = gcloudClustersProcess.waitFor();

            if (exitCode != 0) {
                if (output.contains("404")) {
                    log.debug("{}: {}", exitCode, output);
                    throw new ClusterDoesNotExistException(output);
                } else {
                    log.warn("{}: {}", exitCode, output);
                    throw new RuntimeException(("Got non-zero exit code from gcloud container clusters get-credentials: " + output));
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not execute command to set gcloud context", e);
        }
    }

    /**
     * Call `gcloud config config-helper` to get OAuth token
     * Synchronized to prevent race conditions with different threads calling gcloud, which is not thread safe
     */
    public static synchronized String getGcloudToken() {

        try {
            ProcessBuilder gcloudConfig = new ProcessBuilder("gcloud",
                    "config",
                    "config-helper",
                    "--format",
                    "value(credential.access_token)").redirectErrorStream(true);
            Process gcloudConfigProcess = gcloudConfig.start();
            String output = new String(gcloudConfigProcess.getInputStream().readAllBytes());
            int exitCode = gcloudConfigProcess.waitFor();
            if (exitCode != 0) {
                log.warn("{}: {}", exitCode, output);
                throw new RuntimeException(("Got " + exitCode + " exit code from gcloud config: " + output));
            }
            return output.strip();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not execute command to get gcloud token", e);
        }
    }

    /**
     * Generate context name that gcloud populates in kubeconfig when calling get-credentials
     */
    public static String getGkeContextName(String project, String zone, String cluster) {

        return "gke_" + project + "_" + zone + "_" + cluster;
    }
}
