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
package org.hawkular.agent.monitor.inventory;

import org.hawkular.agent.monitor.scheduler.config.MonitoredEndpoint;
import org.jgrapht.event.VertexSetListener;

/**
 * Just a container that holds the different managers needed to keep track of inventory.
 *
 * @param <RT> resource type
 * @param <RTS> resource type set
 * @param <MT> metric type
 * @param <MTS> metric type set
 * @param <AT> avail type
 * @param <ATS> avail type set
 * @param <O> operation type
 * @param <RCPT> resource configuration property definition
 * @param <R> resource
 * @param <ME> monitored endpoint
 *
 * @author John Mazzitelli
 */
public abstract class InventoryManager< //
RT extends ResourceType<MT, AT, O, RCPT>, //
RTS extends ResourceTypeSet<RT>, //
MT extends MetricType, //
MTS extends MetricTypeSet<MT>, //
AT extends AvailType, //
ATS extends AvailTypeSet<AT>, //
O extends Operation<RT>, //
RCPT extends ResourceConfigurationPropertyType<RT>, //
R extends Resource<RT, ?, ?, ?, ?>, //
ME extends MonitoredEndpoint> {

    private final MetadataManager<RT, RTS, MT, MTS, AT, ATS, O, RCPT> metadataManager;
    private final ResourceManager<R> resourceManager;
    private final ManagedServer managedServer;
    private final ME endpoint;
    private final String feedId; // identifies the agent that owns this inventory

    public InventoryManager(
            String feedId,
            MetadataManager<RT, RTS, MT, MTS, AT, ATS, O, RCPT> metadataManager,
            ResourceManager<R> resourceManager,
            ManagedServer managedServer,
            ME endpoint) {
        this.feedId = feedId;
        this.metadataManager = metadataManager;
        this.resourceManager = resourceManager;
        this.managedServer = managedServer;
        this.endpoint = endpoint;
    }

    public MetadataManager<RT, RTS, MT, MTS, AT, ATS, O, RCPT> getMetadataManager() {
        return metadataManager;
    }

    public ResourceManager<R> getResourceManager() {
        return resourceManager;
    }

    public ManagedServer getManagedServer() {
        return managedServer;
    }

    public ME getEndpoint() {
        return endpoint;
    }

    /**
     * @return the feed identifier which simply identifies the agent that owns the inventory
     */
    public String getFeedId() {
        return feedId;
    }

    /**
     * Populate the inventory with resources that can be automatically discovered.
     * Only those resources of known types (see {@link MetadataManager#getResourceTypeManager()}) will be discovered.
     *
     * Once this returns successfully, {@link #getResourceManager()} should be populated with resources.
     *
     * @param listener if not null, will be a listener that gets notified when resources are discovered
     */
    public abstract void discoverResources(VertexSetListener<R> listener);
}
