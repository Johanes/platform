package org.wso2.carbon.nhttp.test;

/*  Copyright (c) WSO2 Inc. (http://wso2.com) All Rights Reserved.

  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*
*/

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.integration.core.AuthenticateStub;
import org.wso2.carbon.integration.core.FrameworkSettings;
import org.wso2.carbon.integration.core.TestTemplate;
import org.wso2.carbon.integration.core.utils.ArtifactReader;
import org.wso2.carbon.integration.core.utils.StockQuoteClient;
import org.wso2.carbon.mediation.configadmin.stub.ConfigServiceAdminStub;
import java.net.URL;
import java.net.URLConnection;

public class ChunkDisabledTest extends TestTemplate {
    private static final Log log = LogFactory.getLog(ChunkDisabledTest.class);

    @Override
    public void init() {
        log.info("Initializing Chunk Disabled Tests");
        log.debug("Chunk Disabled Tests Initialised");
    }

    @Override
    public void runSuccessCase() {
        log.debug("Running Chunk Disabled SuccessCase ");
        StockQuoteClient stockQuoteClient = new StockQuoteClient();
        OMElement result = null;

        try {
            AuthenticateStub authenticateStub = new AuthenticateStub();
            ConfigServiceAdminStub configServiceAdminStub = new ConfigServiceAdminStub("https://" + FrameworkSettings.HOST_NAME + ":" + FrameworkSettings.HTTPS_PORT + "/services/ConfigServiceAdmin");
            authenticateStub.authenticateAdminStub(configServiceAdminStub, sessionCookie);
            ArtifactReader artifactReader = new ArtifactReader();

            OMElement omElement = artifactReader.getOMElement(ChunkDisabledTest.class.getResource("/chunking_disabled.xml").getPath());

            configServiceAdminStub.updateConfiguration(omElement);

            String trpUrl = null;

            if (FrameworkSettings.STRATOS.equalsIgnoreCase("false")) {
                trpUrl = "http://" + FrameworkSettings.HOST_NAME + ":" + FrameworkSettings.HTTP_PORT;
                result = stockQuoteClient.stockQuoteClientforProxy(trpUrl, null, "IBM");
            } else if (FrameworkSettings.STRATOS.equalsIgnoreCase("true")) {
                result = stockQuoteClient.stockQuoteClientforProxy("http://" + FrameworkSettings.HOST_NAME + "/services/" + FrameworkSettings.TENANT_NAME + "/", null, "IBM");
            }
            log.info(result);
            System.out.println(result);
            //<ns:getQuoteResponse xmlns:ns="http://services.samples"><ns:return xmlns:ax21="http://services.samples/xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ax21:GetQuoteResponse"><ax21:change>4.488382103511101</ax21:change><ax21:earnings>-8.983387861433535</ax21:earnings><ax21:high>-54.85240340206867</ax21:high><ax21:last>55.185600908158364</ax21:last><ax21:lastTradeTimestamp>Tue Oct 19 11:18:43 IST 2010</ax21:lastTradeTimestamp><ax21:low>-54.739935633390765</ax21:low><ax21:marketCap>1.4210456725425582E7</ax21:marketCap><ax21:name>IBM Company</ax21:name><ax21:open>57.531671038463294</ax21:open><ax21:peRatio>23.993007092941014</ax21:peRatio><ax21:percentageChange>7.542848922991673</ax21:percentageChange><ax21:prevClose>59.50513061225283</ax21:prevClose><ax21:symbol>IBM</ax21:symbol><ax21:volume>15739</ax21:volume></ns:return></ns:getQuoteResponse>

            /*Test HTTP Header Contents*/

            URL url = new URL(trpUrl);
            URLConnection conn = url.openConnection();

            for (int i = 0; ; i++) {
                String name = conn.getHeaderFieldKey(i);
                String value = conn.getHeaderField(i);
                if (name == null && value == null) {
                    break;
                }
                if (name == null) {
                    System.out.println("Server HTTP version, Response code:");
                    System.out.println(value);
                    System.out.print("\n");
                } else {
                    System.out.println(name + "=" + value);
                }
            }


        } catch (Exception
                e) {
            e.printStackTrace();
            log.error("Chunk Disabled Test doesn't work : " + e.getMessage());

        }

    }


    @Override
    public void runFailureCase
            () {
    }

    @Override
    public void cleanup() {

    }
}
