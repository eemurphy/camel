/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.http4;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpsTwoComponentsSslContextParametersGetTest extends BaseHttpsTest {

    private int port2;
    private HttpServer localServer;
    
    @BindToRegistry("x509HostnameVerifier")
    private NoopHostnameVerifier hostnameVerifier = new NoopHostnameVerifier();
    
    @BindToRegistry("sslContextParameters")
    private SSLContextParameters sslContextParameters = new SSLContextParameters();
    
    @BindToRegistry("sslContextParameters2")
    private SSLContextParameters sslContextParameters2 = new SSLContextParameters();
    
    @BindToRegistry("https-foo")
    private HttpComponent httpComponent = new HttpComponent();
    
    @BindToRegistry("https-bar")
    private HttpComponent httpComponent1 = new HttpComponent();
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void httpsTwoDifferentSSLContextNotSupported() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port2 = AvailablePortFinder.getNextAvailable(localServer.getLocalPort());

                from("direct:foo")
                        .to("https-foo://127.0.0.1:" + localServer.getLocalPort() + "/mail?x509HostnameVerifier=#x509HostnameVerifier&sslContextParameters=#sslContextParameters");

                from("direct:bar")
                        .to("https-bar://127.0.0.1:" + port2 + "/mail?x509HostnameVerifier=#x509HostnameVerifier&sslContextParameters=#sslContextParameters2");
            }
        });

        context.start();

        // should be able to startup
        Thread.sleep(500);

        context.stop();
    }

}
