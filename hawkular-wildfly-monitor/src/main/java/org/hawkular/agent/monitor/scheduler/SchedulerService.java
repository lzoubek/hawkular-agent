/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.agent.monitor.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailInstance;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricInstance;
import org.hawkular.agent.monitor.inventory.jmx.JMXAvailInstance;
import org.hawkular.agent.monitor.inventory.jmx.JMXMetricInstance;
import org.hawkular.agent.monitor.inventory.platform.PlatformAvailInstance;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricInstance;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.AvailJMXPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.AvailPlatformPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.hawkular.agent.monitor.scheduler.config.JMXPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.PlatformEndpoint;
import org.hawkular.agent.monitor.scheduler.config.PlatformPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.polling.IntervalBasedScheduler;
import org.hawkular.agent.monitor.scheduler.polling.Scheduler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.scheduler.polling.dmr.AvailDMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.AvailDMRTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.dmr.DMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.jmx.AvailJMXTask;
import org.hawkular.agent.monitor.scheduler.polling.jmx.AvailJMXTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.jmx.JMXTask;
import org.hawkular.agent.monitor.scheduler.polling.jmx.MetricJMXTask;
import org.hawkular.agent.monitor.scheduler.polling.jmx.MetricJMXTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.platform.MetricPlatformTask;
import org.hawkular.agent.monitor.scheduler.polling.platform.MetricPlatformTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.platform.PlatformTask;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.storage.AvailBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.MetricBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.StorageAdapter;

/**
 * The core service that schedules tasks and stores the data resulting from those tasks to its storage adapter.
 */
public class SchedulerService {
    private static final MsgLogger log = AgentLoggers.getLogger(SchedulerService.class);
    private final SchedulerConfiguration schedulerConfig;
    private final ServerIdentifiers selfId;
    private final ModelControllerClientFactory localDMRClientFactory;
    private final Diagnostics diagnostics;
    private final Scheduler metricScheduler;
    private final Scheduler availScheduler;
    private final MetricBufferedStorageDispatcher metricCompletionHandler;
    private final AvailBufferedStorageDispatcher availCompletionHandler;

    private boolean started = false;

    public SchedulerService(
            SchedulerConfiguration configuration,
            ServerIdentifiers selfId,
            Diagnostics diagnostics,
            StorageAdapter storageAdapter,
            ModelControllerClientFactory localDMRClientFactory) {

        this.schedulerConfig = configuration;

        // for those tasks that require a DMR client to our own WildFly server, this factory can provide those clients
        this.localDMRClientFactory = localDMRClientFactory;

        // this helps identify where we are running
        this.selfId = selfId;

        // metrics for our own internals
        this.diagnostics = diagnostics;

        // create the schedulers - we use two: one for metric collections and one for avail checks
        this.metricCompletionHandler = new MetricBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.metricScheduler = new IntervalBasedScheduler(this, "Hawkular-Monitor-Scheduler-Metrics",
                configuration.getMetricSchedulerThreads());

        this.availCompletionHandler = new AvailBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.availScheduler = new IntervalBasedScheduler(this, "Hawkular-Monitor-Scheduler-Avail",
                configuration.getAvailSchedulerThreads());
    }

    public ServerIdentifiers getSelfIdentifiers() {
        return this.selfId;
    }

    public Diagnostics getDiagnostics() {
        return this.diagnostics;
    }

    public void start() {
        if (started) {
            return; // already started
        }

        log.infoStartingScheduler();

        // turn metric DMR refs into Tasks and schedule them now
        List<Task> metricTasks = createMetricDMRTasks(schedulerConfig.getDMRMetricsToBeCollected());

        // turn metric JMX refs into Tasks and schedule them now
        metricTasks.addAll(createMetricJMXTasks(schedulerConfig.getJMXMetricsToBeCollected()));

        // turn platform metrics into Tasks and schedule them now
        metricTasks.addAll(createMetricPlatformTasks(schedulerConfig.getPlatformMetricsToBeCollected()));

        // turn avail DMR refs into Tasks and schedule them now
        List<Task> availTasks = createAvailDMRTasks(schedulerConfig.getDMRAvailsToBeChecked());

        // turn platform avails into Tasks and schedule them now
        availTasks.addAll(createAvailPlatformTasks(schedulerConfig.getPlatformAvailsToBeChecked()));

        // turn avail JMX refs into Tasks and schedule them now
        availTasks.addAll(createAvailJMXTasks(schedulerConfig.getJMXAvailsToBeChecked()));

        // start the collections
        this.metricCompletionHandler.start();
        this.metricScheduler.schedule(metricTasks);

        this.availCompletionHandler.start();
        this.availScheduler.schedule(availTasks);

        started = true;
    }

    public void stop() {
        if (!started) {
            return; // already stopped
        }

        log.infoStoppingScheduler();

        // stop completion handlers
        this.metricCompletionHandler.shutdown();
        this.availCompletionHandler.shutdown();

        // stop the schedulers
        this.metricScheduler.shutdown();
        this.availScheduler.shutdown();

        started = false;
    }

    public Runnable getTaskGroupRunnable(TaskGroup group) {
        switch (group.getType()) {
            case METRIC: {
                // we are guaranteed the first task is the same kind as all the rest
                Task firstTask = group.getTask(0);
                if (DMRTask.class.isInstance(firstTask)) {
                    // we are guaranteed that all tasks in a group refer to the same endpoint
                    DMREndpoint endpoint = ((DMRTask) firstTask).getEndpoint();
                    ModelControllerClientFactory factory;
                    if (endpoint instanceof LocalDMREndpoint) {
                        factory = this.localDMRClientFactory;
                    } else {
                        factory = new ModelControllerClientFactoryImpl(endpoint);
                    }
                    return new MetricDMRTaskGroupRunnable(group, metricCompletionHandler, getDiagnostics(), factory);
                } else if (PlatformTask.class.isInstance(firstTask)) {
                    return new MetricPlatformTaskGroupRunnable(group, metricCompletionHandler, diagnostics);
                } else if (JMXTask.class.isInstance(firstTask)) {
                    JMXEndpoint endpoint = ((JMXTask) firstTask).getEndpoint();
                    return new MetricJMXTaskGroupRunnable(group, metricCompletionHandler, diagnostics,
                            new JmxClientFactoryImpl(endpoint));
                } else {
                    throw new UnsupportedOperationException("Unsupported metric group: " + group);
                }
            }

            case AVAIL: {
                // we are guaranteed the first task is the same kind as all the rest
                Task firstTask = group.getTask(0);
                if (DMRTask.class.isInstance(firstTask)) {
                    // we are guaranteed that all tasks in a group refer to the same endpoint
                    DMREndpoint endpoint = ((DMRTask) firstTask).getEndpoint();
                    ModelControllerClientFactory factory;
                    if (endpoint instanceof LocalDMREndpoint) {
                        factory = this.localDMRClientFactory;
                    } else {
                        factory = new ModelControllerClientFactoryImpl(endpoint);
                    }
                    return new AvailDMRTaskGroupRunnable(group, availCompletionHandler, getDiagnostics(), factory);
                } else if (PlatformTask.class.isInstance(firstTask)) {
                    new UnsupportedOperationException("Avail checks for platform resources are not supported");
                } else if (JMXTask.class.isInstance(firstTask)) {
                    JMXEndpoint endpoint = ((JMXTask) firstTask).getEndpoint();
                    return new AvailJMXTaskGroupRunnable(group, availCompletionHandler, diagnostics,
                            new JmxClientFactoryImpl(endpoint));
                } else {
                    throw new UnsupportedOperationException("Unsupported avail group: " + group);
                }
            }

            default: {
                throw new IllegalArgumentException("Bad group [" + group + "]. Please report this bug.");
            }
        }
    }

    private List<Task> createMetricDMRTasks(Map<DMREndpoint, List<DMRMetricInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<DMREndpoint, List<DMRMetricInstance>> entry : map.entrySet()) {
            DMREndpoint dmrEndpoint = entry.getKey();
            for (DMRMetricInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                DMRPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new MetricDMRTask(propRef.getInterval(), dmrEndpoint, propRef.getAddress(), attribute,
                        subref, instance));
            }
        }

        return tasks;
    }

    private List<Task> createAvailDMRTasks(Map<DMREndpoint, List<DMRAvailInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<DMREndpoint, List<DMRAvailInstance>> entry : map.entrySet()) {
            DMREndpoint dmrEndpoint = entry.getKey();
            for (DMRAvailInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                AvailDMRPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new AvailDMRTask(propRef.getInterval(), dmrEndpoint, propRef.getAddress(), attribute,
                        subref, instance, propRef.getUpRegex()));
            }
        }

        return tasks;
    }

    private List<Task> createMetricJMXTasks(Map<JMXEndpoint, List<JMXMetricInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<JMXEndpoint, List<JMXMetricInstance>> entry : map.entrySet()) {
            JMXEndpoint jmxEndpoint = entry.getKey();
            for (JMXMetricInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                JMXPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new MetricJMXTask(propRef.getInterval(), jmxEndpoint, propRef.getObjectName(), attribute,
                        subref, instance));
            }
        }

        return tasks;
    }

    private List<Task> createAvailJMXTasks(Map<JMXEndpoint, List<JMXAvailInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<JMXEndpoint, List<JMXAvailInstance>> entry : map.entrySet()) {
            JMXEndpoint jmxEndpoint = entry.getKey();
            for (JMXAvailInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                AvailJMXPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new AvailJMXTask(propRef.getInterval(), jmxEndpoint, propRef.getObjectName(), attribute,
                        subref, instance, propRef.getUpRegex()));
            }
        }

        return tasks;
    }

    private List<Task> createMetricPlatformTasks(Map<PlatformEndpoint, List<PlatformMetricInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Entry<PlatformEndpoint, List<PlatformMetricInstance>> entry : map.entrySet()) {
            PlatformEndpoint endpoint = entry.getKey();
            for (PlatformMetricInstance instance : entry.getValue()) {
                PlatformPropertyReference propRef = instance.getProperty();
                tasks.add(new MetricPlatformTask(propRef.getInterval(), endpoint, instance));
            }
        }

        return tasks;
    }

    private List<Task> createAvailPlatformTasks(Map<PlatformEndpoint, List<PlatformAvailInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Entry<PlatformEndpoint, List<PlatformAvailInstance>> entry : map.entrySet()) {
            PlatformEndpoint endpoint = entry.getKey();
            for (PlatformAvailInstance instance : entry.getValue()) {
                AvailPlatformPropertyReference propRef = instance.getProperty();
                throw new UnsupportedOperationException("Platform avail checking not yet supported");
                //tasks.add(new AvailPlatformTask(propRef.getInterval(), endpoint, instance));
            }
        }

        return tasks;
    }
}
