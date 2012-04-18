/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.agent.internal.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class AgentServerURL {
    private String protocol;
    private String host;
    private int port;

    public AgentServerURL(String url) throws MalformedURLException {
        URL theUrl;
        if (!url.contains("tcp")) {
            throw new MalformedURLException("tcp protocol not found in the URL " + url);
        } else {
            theUrl = new URL(url.replaceFirst("tcp", "http"));
        }
        this.protocol = theUrl.getProtocol();
        this.host = theUrl.getHost();
        this.port = theUrl.getPort();
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
