
package io.quarkus.kubernetes.deployment;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkus.container.deployment.ContainerConfig;
import io.quarkus.container.deployment.DockerBuild.OutputFilter;
import io.quarkus.deployment.util.ExecUtil;

public class KubernetesDeploy implements BooleanSupplier {

    private final Logger LOGGER = Logger.getLogger(KubernetesDeploy.class);

    private KubernetesConfig kubernetesConfig;
    private ContainerConfig containerConfig;

    KubernetesDeploy(ContainerConfig containerConfig, KubernetesConfig kubernetesConfig) {
        this.containerConfig = containerConfig;
        this.kubernetesConfig = kubernetesConfig;
    }

    @Override
    public boolean getAsBoolean() {
        if (containerConfig.deploy) {
            OutputFilter filter = new OutputFilter();
            try {
                if (kubernetesConfig.getDeploymentTarget().contains(DeploymentTarget.OPENSHIFT)) {
                    if (ExecUtil.exec(new File("."), filter, "oc", "version")) {
                        Optional<String> version = getServerVersionFromOc(filter.getLines());
                        version.ifPresent(v -> LOGGER.info("Found Kubernetes version:" + v));
                        return true;
                    }
                }
                if (ExecUtil.exec(new File("."), filter, "kubectl", "version")) {
                    Optional<String> version = getServerVersionFromKubectl(filter.getLines());
                    version.ifPresent(v -> LOGGER.info("Found Kubernetes version:" + v));
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static Optional<String> getServerVersionFromOc(List<String> lines) {
        return lines.stream()
                .filter(l -> l.startsWith("kubernetes"))
                .map(l -> l.split(" "))
                .filter(a -> a.length > 2)
                .map(a -> a[1])
                .findFirst();
    }

    private static Optional<String> getServerVersionFromKubectl(List<String> lines) {
        return lines.stream()
                .filter(l -> l.startsWith("Server Version"))
                .map(l -> l.split("\""))
                .filter(a -> a.length > 5)
                .map(a -> a[5])
                .findFirst();
    }

    private static class OutputFilter implements Function<InputStream, Runnable> {
        private final List<String> list = new ArrayList();

        @Override
        public Runnable apply(InputStream is) {
            return () -> {
                try (InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader reader = new BufferedReader(isr)) {

                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        list.add(line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error reading stream.", e);
                }
            };
        }

        public List<String> getLines() {
            return list;
        }
    }
}
