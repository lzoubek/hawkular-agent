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
package org.hawkular.cmdgw.ws.test;

import static org.mockito.Mockito.verify;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocket.PayloadType;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.BufferedSource;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class JdbcDriverCommandITest extends AbstractCommandITest {
    private static final String driverFileNameAfterAdd = "driver-after-add.node.txt";
    private static final String driverName = "mysql";

    private static ModelNode driverAddress() {
        return new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources").add("jdbc-driver", driverName);
    }

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddJdbcDriver() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        final ModelNode driverAddress = driverAddress();

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, driverAddress, false);

            /* OK, h2 is there let's add a new MySQL Driver */
            final String driverJarRawUrl = "http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.36/"
                    + "mysql-connector-java-5.1.36.jar";
            URL driverJarUrl = new URL(driverJarRawUrl);
            final String driverJarName = new File(driverJarUrl.getPath()).getName();

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "AddJdbcDriverRequest={\"authentication\":" + authentication + "," //
                                    + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                                    + "\"driverName\":\"" + driverName + "\"," //
                                    + "\"driverClass\":\"com.mysql.jdbc.Driver\"," //
                                    + "\"driverMajorVersion\":\"5\"," //
                                    + "\"driverMinorVersion\":\"1\"," //
                                    + "\"moduleName\":\"com.mysql\"," //
                                    + "\"driverJarName\":\"" + driverJarName + "\"" //
                                    + "}",
                            driverJarUrl);
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;
            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("AddJdbcDriverResponse={" //
                    + "\"driverName\":\"" + driverName + "\"," //
                    + "\"resourcePath\":\"" + wfPath + "\"," //
                    + "\"destinationSessionId\":\"" + sessionId + "\"," //
                    + "\"status\":\"OK\"," //
                    + "\"message\":\"Added JDBC Driver: " + driverName + "\"" //
                    + "}", receivedMessages.get(i++).readUtf8());

            assertNodeEquals(mcc, driverAddress, getClass(), driverFileNameAfterAdd, false);
        }
    }

    @Test(dependsOnMethods = { "testAddJdbcDriver" })
    public void testRemoveJdbcDriver() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        final ModelNode driverAddress = driverAddress();

        String removePath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/jdbc-driver=" + driverName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            ModelNode datasourcesPath = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources");
            assertResourceCount(mcc, datasourcesPath, "jdbc-driver", 2);
            assertResourceExists(mcc, driverAddress, true);

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "RemoveJdbcDriverRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + removePath + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("RemoveJdbcDriverResponse={"//
                    + "\"resourcePath\":\"" + removePath.toString() + "\"," //
                    + "\"destinationSessionId\":\"" + sessionId + "\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Remove] on a [JDBC Driver] given by Inventory path [" + removePath
                    + "]\""//
                    + "}", receivedMessages.get(i++).readUtf8());

            assertResourceExists(mcc, driverAddress, false);

        }
    }

}
