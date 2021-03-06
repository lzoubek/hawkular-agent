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
package org.hawkular.wildfly.module.installer;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeploymentConfiguration {

    /**
     * Default path to to server config file (relative to {@link #jbossHome})
     */
    public static String DEFAULT_SERVER_CONFIG = "standalone/configuration/standalone.xml";

    /**
     * Location of JBoss input module.zip This file should have JBoss module
     * directory structure so it can be laid down to {@link #modulesHome} directory.
     */
    private URL module;

    /**
     * Location of AS7/WildFly server to deploy to
     */
    private File jbossHome;

    /**
     * Location of modules home (either relative to {@link #jbossHome} or absolute).
     * Set this value unless your structure inside {@link #moduleZip} does not include path to modules.
     * Default is null, which means it is detected - for Wildfly, this path would be "modules/system/layers/base",
     * for older AS7 versions it's just "modules".
     */
    private String modulesHome;

    /**
     * Location of source server configuration file (standalone.xml) write to
     * (can be either relative to jbossHome or absolute)
     */
    private String sourceServerConfig = DEFAULT_SERVER_CONFIG;

    /**
     * Location of target server configuration file (standalone.xml) write to
     * (can be either relative to jbossHome or absolute)
     */
    private String targetServerConfig = DEFAULT_SERVER_CONFIG;

    /**
     * Location where deployer will backup original server configuration file (standalone.xml)
     * in case {@link #sourceServerConfig} and {@link #targetServerConfig} are same.
     * Can be either relative to {@link #jbossHome} or absolute path.
     */
    private String serverConfigBackup = DEFAULT_SERVER_CONFIG + ".backup";

    /**
     * Location of subsystem content to be inserted into standalone.xml
     */
    private URL subsystem;

    /**
     * Location of socket-binding content to be inserted into standalone.xml
     */
    private URL socketBinding;

    /**
     * List of socket-binding-groups to set socketBinding in (only applies when socketBinding exists)
     * Default : ["standard-sockets"]
     */
    private Set<String> socketBindingGroups = new HashSet<>();

    /**
     * List of data to be inserted to {@link #serverConfig}. This is pretty powerful
     * stuff to put/replace any XML content anywhere in
     * {@link #serverConfig}
     */
    private List<XmlEdit> edit;

    /**
     * List of target profiles (only applies when {@link #domain} is equal to true)
     */
    private Set<String> profiles = new HashSet<>();

    /**
     * Denotes whether we setup standalone-*.xml or domain.xml
     */
    private boolean domain;

    /**
     * Causes {@link ExtensionDeploymentException} to be thrown if any of <strong>select</strong>
     * expression within {@link #edit} does not match any node (thus it wouldn't update
     * {@link #serverConfig})
     */
    private boolean failNoMatch;

    public Set<String> getProfiles() {
        return profiles;
    }

    public boolean isDomain() {
        return domain;
    }

    public URL getModule() {
        return module;
    }

    public File getJbossHome() {
        return jbossHome;
    }

    public String getModulesHome() {
        return modulesHome;
    }

    public String getSourceServerConfig() {
        return sourceServerConfig;
    }

    public String getTargetServerConfig() {
        return targetServerConfig;
    }

    public String getServerConfigBackup() {
        return serverConfigBackup;
    }

    public URL getSubsystem() {
        return subsystem;
    }

    public URL getSocketBinding() {
        return socketBinding;
    }

    public void setDomain(boolean domain) {
        this.domain = domain;
    }

    public void setProfiles(Set<String> profiles) {
        this.profiles = profiles;
    }

    public Set<String> getSocketBindingGroups() {
        return socketBindingGroups;
    }

    public List<XmlEdit> getEdit() {
        if (edit == null) {
            edit = new ArrayList<>();
        }
        return edit;
    }

    public boolean isFailNoMatch() {
        return failNoMatch;
    }

    public void setModule(URL module) {
        this.module = module;
    }

    public void setJbossHome(File jbossHome) {
        this.jbossHome = jbossHome;
    }

    public void setModulesHome(String modulesHome) {
        this.modulesHome = modulesHome;
    }

    public void setSourceServerConfig(String sourceServerConfig) {
        this.sourceServerConfig = sourceServerConfig;
    }

    public void setTargetServerConfig(String targetServerConfig) {
        this.targetServerConfig = targetServerConfig;
    }

    public void setServerConfigBackup(String serverConfigBackup) {
        this.serverConfigBackup = serverConfigBackup;
    }

    public void setSubsystem(URL subsystem) {
        this.subsystem = subsystem;
    }

    public void setSocketBinding(URL socketBinding) {
        this.socketBinding = socketBinding;
    }

    public void setSocketBindingGroups(Set<String> socketBindingGroups) {
        this.socketBindingGroups = socketBindingGroups;
    }

    public void setEdit(List<XmlEdit> edit) {
        this.edit = edit;
    }

    public void setFailNoMatch(boolean failNoMatch) {
        this.failNoMatch = failNoMatch;
    }

    public static class Builder {
        private final DeploymentConfiguration configuration = new DeploymentConfiguration();

        public Builder module(URL module) {
            configuration.setModule(module);
            return this;
        }

        public Builder socketBinding(URL socketBinding) {
            configuration.setSocketBinding(socketBinding);
            return this;
        }

        public Builder jbossHome(File jbossHome) {
            configuration.setJbossHome(jbossHome);
            return this;
        }

        public Builder addXmlEdit(XmlEdit edit) {
            configuration.getEdit().add(edit);
            return this;
        }

        public Builder domain(boolean domain) {
            configuration.setDomain(domain);
            return this;
        }

        public Builder addProfile(String profile) {
            configuration.getProfiles().add(profile);
            return this;
        }

        public Builder addSocketBindingGroup(String groupName) {
            configuration.getSocketBindingGroups().add(groupName);
            return this;
        }

        public Builder serverConfig(String serverConfig) {
            configuration.setSourceServerConfig(serverConfig);
            configuration.setTargetServerConfig(serverConfig);
            return this;
        }

        public DeploymentConfiguration build() {
            // add defaults if needed
            if (configuration.isDomain() && configuration.getProfiles().isEmpty()) {
                addProfile("default");
            }
            if (configuration.getSocketBindingGroups().isEmpty()) {
                configuration.getSocketBindingGroups().add("standard-sockets");
            }
            return configuration;
        }

    }
    /**
     * create Configuration Builder
     * @return Configuration Builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
