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

import java.util.List;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
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
public class ExecuteOperationCommandITest extends AbstractCommandITest {

    @Test(dependsOnGroups = { "no-dependencies" }, groups = "exclusive-inventory-access")
    public void testExecuteOperation() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        AssertJUnit.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        final String deploymentName = "hawkular-helloworld-war.war";
        Resource deployment = getResource("/test/" + feedId + "/resourceTypes/Deployment/resources",
                (r -> r.getId().endsWith("=" + deploymentName)));

        Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                send(webSocket,
                        "ExecuteOperationRequest={\"authentication\":" + authentication + ", " //
                                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\"," //
                                + "\"operationName\":\"Redeploy\"" //
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

        String successRe = "\\QGenericSuccessResponse={\"message\":"
                + "\"The request has been forwarded to feed [" + wfPath.ids().getFeedId() + "] (\\E.*";

        String msg = receivedMessages.get(i++).readUtf8();
        AssertJUnit.assertTrue("[" + msg + "] does not match [" + successRe + "]", msg.matches(successRe));

        AssertJUnit.assertEquals("ExecuteOperationResponse={" //
                + "\"operationName\":\"Redeploy\"," //
                + "\"resourcePath\":\"" + deployment.getPath() + "\"," //
                + "\"destinationSessionId\":\""+ sessionId +"\"," //
                + "\"status\":\"OK\"," //
                + "\"message\":\"Performed [Redeploy] on a [DMR Node] given by Inventory path [" //
                + deployment.getPath() + "]\"" //
                + "}", receivedMessages.get(i++).readUtf8());

    }

}
