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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MulticastNoStopOnExceptionTest extends ContextTestSupport {

    @Test
    public void testMulticastNoStopOnExceptionOk() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:baz").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMulticastNoStopOnExceptionStop() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        // we do not stop so we should continue and thus baz receives 1 message
        getMockEndpoint("mock:baz").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast()
                        .to("direct:foo", "direct:bar", "direct:baz")
                    .end()
                    .to("mock:result");

                from("direct:foo").to("mock:foo");

                from("direct:bar").process(new MyProcessor()).to("mock:bar");

                from("direct:baz").to("mock:baz");
            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Forced");
            }
        }
    }
}