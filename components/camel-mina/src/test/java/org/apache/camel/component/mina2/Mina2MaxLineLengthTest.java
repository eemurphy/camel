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
package org.apache.camel.component.mina2;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class Mina2MaxLineLengthTest extends BaseMina2Test {

    @Test
    public void testSendToServer() {
        String request = "";
        for (int c = 0; c < 4000; c++) {
            request += "A";
        }

        // START SNIPPET: e3
        String out = (String) template.requestBody(String.format("mina:tcp://localhost:%1$s?sync=true&textline=true&encoderMaxLineLength=5000&decoderMaxLineLength=5000", getPort()), request);
        assertEquals(request, out);
        // END SNIPPET: e3
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // lets setup a server on port %1$s
                // we set the sync option so we will send a reply
                // and we let the request-reply be processed in the MyServerProcessor
                from(String.format("mina:tcp://localhost:%1$s?sync=true&textline=true&encoderMaxLineLength=5000&decoderMaxLineLength=5000", getPort())).process(new MyServerProcessor());
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    private static class MyServerProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            // get the input from the IN body
            String request = exchange.getIn().getBody(String.class);
            // echo back the response on the OUT body
            exchange.getOut().setBody(request);
        }
    }
    // END SNIPPET: e2
}
