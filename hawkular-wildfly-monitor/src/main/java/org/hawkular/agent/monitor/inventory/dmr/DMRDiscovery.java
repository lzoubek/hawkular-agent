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
package org.hawkular.agent.monitor.inventory.dmr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.DepthFirstIterator;

/**
 * Discovers resources for a given DMR endpoint.
 */
public class DMRDiscovery {
    private static final Logger LOG = Logger.getLogger(DMRDiscovery.class);

    private final ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> resourceTypeManager;
    private final DMREndpoint dmrEndpoint;
    private final ModelControllerClientFactory clientFactory;
    private final DirectedGraph<DMRResource, DefaultEdge> resourcesGraph;

    /**
     * Creates the discovery object for the given server endpoint. Only resources of the given types
     * will be discovered. To connect to and query the server endpoint, the given client factory will
     * be used to create clients.
     *
     * @param dmrEndpoint the endpoint of the server to be queried
     * @param rtm the types to be discovered
     * @param clientFactory will create clients used to communicate with the endpoint
     */
    public DMRDiscovery(DMREndpoint dmrEndpoint, ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm,
            ModelControllerClientFactory clientFactory) {
        this.resourceTypeManager = rtm;
        this.dmrEndpoint = dmrEndpoint;
        this.clientFactory = clientFactory;
        this.resourcesGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        LOG.tracef("Endpoint [%s] resource type graph -> %s", dmrEndpoint, rtm.getResourceTypesGraph());
    }

    /**
     * Performs the discovery. A graph is returned that contains all the discovered resources.
     * The graph is nothing more than a tree with parent resources at the top of the tree and
     * children at the bottom (that is to say, a resource will have an outgoing edge to its parent
     * and incoming edges from its children).
     * @return 
     *
     * @return tree graph of all discovered resources
     *
     * @throws Exception if discovery failed
     */
    public DirectedGraph<DMRResource, DefaultEdge> discoverAllResources() throws Exception {
        try (ModelControllerClient mcc = clientFactory.createClient()) {
            Set<DMRResourceType> rootTypes = this.resourceTypeManager.getRootResourceTypes();
            for (DMRResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType, mcc);
            }
            logTreeGraph("Discovered resources", resourcesGraph);
            return resourcesGraph;
        } catch (Exception e) {
            throw new Exception("Failed to execute discovery for endpoint [" + this.dmrEndpoint + "]", e);
        }
    }

    private void discoverChildrenOfResourceType(DMRResource parent, DMRResourceType type, ModelControllerClient mcc) {
        try {
            Map<Address, ModelNode> resources;

            CoreJBossASClient client = new CoreJBossASClient(mcc);
            Address parentAddr = (parent == null) ? Address.root() : parent.getAddress().clone();
            Address addr = parentAddr.add(Address.parse(type.getPath()));

            LOG.debugf("Discovering children of [%s] of type [%s] using address query [%s]", parent, type, addr);

            // can return a single resource (type of OBJECT) or a list of them (type of LIST whose items are OBJECTS)
            ModelNode results = client.readResource(addr);
            if (results == null) {
                resources = Collections.emptyMap();
            } else if (results.getType() == ModelType.OBJECT) {
                resources = new HashMap<>(1);
                resources.put(addr, results);
            } else if (results.getType() == ModelType.LIST) {
                resources = new HashMap<>();
                List<ModelNode> list = results.asList();
                for (ModelNode item : list) {
                    resources.put(Address.fromModelNodeWrapper(item, "address"), item);
                }
            } else {
                throw new IllegalStateException("Invalid type - please report this bug: " + results.getType()
                        + " [[" + results.toString() + "]]");
            }

            for (Map.Entry<Address, ModelNode> entry : resources.entrySet()) {
                String resourceName = generateResourceName(type, entry.getKey());
                DMRResource resource = new DMRResource(resourceName, type, parent, entry.getKey());
                LOG.debugf("Discovered [%s]", resource);

                this.resourcesGraph.addVertex(resource);
                if (parent != null) {
                    this.resourcesGraph.addEdge(resource, parent);
                }

                // recursively discover children of child types
                List<DMRResourceType> childTypes = Graphs.predecessorListOf(
                        this.resourceTypeManager.getResourceTypesGraph(), type);
                for (DMRResourceType childType : childTypes) {
                    discoverChildrenOfResourceType(resource, childType, mcc);
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to discover resources in [%s]", this.dmrEndpoint);
        }
    }

    private void logTreeGraph(String logMsg, DirectedGraph<DMRResource, DefaultEdge> graph) {
        if (!LOG.isDebugEnabled()) {
            return;
        }

        StringBuilder graphString = new StringBuilder();
        DepthFirstIterator<DMRResource, DefaultEdge> iter = new DepthFirstIterator<>(graph);
        while (iter.hasNext()) {
            DMRResource resource = iter.next();

            // append some indents based on depth of resource in tree
            DMRResource parent = resource.getParent();
            int depth = 0;
            do {
                if (depth++ > 0) {
                    graphString.append("...");
                }
                parent = parent.getParent();
            } while (parent != null);

            // append resource to string
            graphString.append(resource).append("\n");
        }

        LOG.debugf("%s\n%s", logMsg, graphString);
    }

    private String generateResourceName(DMRResourceType type, Address address) {
        ArrayList<String> args = new ArrayList<>();
        if (!address.isRoot()) {
            List<Property> parts = address.getAddressNode().asPropertyList();
            for (Property part : parts) {
                args.add(part.getName());
                args.add(part.getValue().asString());
            }
        }

        // The name template can have %# where # is the index number of the address part that should be substituted.
        // For example, suppose a resource has an address of "/hello=world/foo=bar" and the template is "Name [%2]".
        // The %2 will get substituted with the second address part (which is "world" - indices start at 1).
        // String.format() requires "$s" after the "%#" to denote the type of value is a string (all our address
        // parts are strings, so we know "$s" is what we want).
        // This replaceAll just replaces all occurrances of "%#" with "%#$s" so String.format will work.
        // We also allow for the special %- notation to mean "the last address part" since that's usually the one we
        // want and sometimes you can't know its positional value.
        String nameTemplate = type.getResourceNameTemplate();
        nameTemplate = nameTemplate.replaceAll("%(\\d+)", "%$1\\$s");
        nameTemplate = nameTemplate.replaceAll("%(-)", "%" + args.size() + "\\$s");

        return String.format(nameTemplate, args.toArray());
    }
}
