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

import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.jolokia.client.J4pClient;

/**
 * Can create clients to remote JMX servers.
 */
public class JmxClientFactoryImpl implements JmxClientFactory {

    private final JMXEndpoint defaultEndpoint;

    public JmxClientFactoryImpl(JMXEndpoint endpoint) {
        this.defaultEndpoint = endpoint;
    }

    @Override
    public J4pClient createClient() {
        return defaultEndpoint.getJmxClient();
    }
}
