/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.registry.metadata.test.service;

import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.governance.services.stub.AddServicesServiceStub;
import org.wso2.carbon.integration.core.TestTemplate;
import org.wso2.carbon.registry.metadata.test.util.TestUtils;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

import static org.wso2.carbon.registry.metadata.test.util.TestUtils.isResourceExist;


public class ServiceAddTest extends TestTemplate {

    private static final Log log = LogFactory.getLog(ServiceAddTest.class);
    private String servicePath = "/_system/governance/services/";
    private String wsdlPath = "/_system/governance/wsdls/";
    private String schemaPath = "/_system/governance/schemas/";

    private AddServicesServiceStub addServicesServiceStub;
    private ResourceAdminServiceStub resourceAdminServiceStub;

    @Override
    public void init() {
        log.info("Initializing Add Service Resource Tests");
        log.debug("Add Service Resource Initialised");
    }

    @Override
    public void runSuccessCase() {
        log.debug("Running SuccessCase");
        addServicesServiceStub = TestUtils.getAddServicesServiceStub(sessionCookie);
        resourceAdminServiceStub = TestUtils.getResourceAdminServiceStub(sessionCookie);

        String resourceName = "info.xml";
        String serviceName = "MyBizServiceName";
        String wsdlName = serviceName + ".wsdl";
        String wsdlNamespacePath = "com/foo/";
        String schemaNamespacePath = "http/bar/org/purchasing/";
        String schemaName = "purchasing.xsd";

        String resource = frameworkPath + File.separator + ".." + File.separator + ".." + File.separator + ".." +
                File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator +
                "resources" + File.separator + resourceName;


        try {
            addServicesServiceStub.addService(fileReader(resource));

            String textContent = resourceAdminServiceStub.getTextContent(servicePath +
                    wsdlNamespacePath + serviceName + "/1/0/0/service");


            if (textContent.indexOf("http://foo.com") != -1) {
                log.info("service content found");

            } else {
                log.error("service content not found");
                Assert.fail("service content not found");
            }


            String textContentWsdl = resourceAdminServiceStub.getTextContent(wsdlPath +
                    wsdlNamespacePath + wsdlName);

            if (textContentWsdl.indexOf("../../../../schemas/http/bar/org/purchasing/purchasing.xsd") != -1) {
                log.info("wsdl content found");

            } else {
                log.error("wsdl content not found");
                Assert.fail("wsdl content not found");
            }
            String textContentSchema = resourceAdminServiceStub.getTextContent(schemaPath +
                    schemaNamespacePath + schemaName);

            if (textContentSchema.indexOf("http://bar.org/purchasing") != -1) {
                log.info("schema content found");

            } else {
                log.error("schema content not found");
                Assert.fail("schema content not found");
            }

            //delete the added resource
            resourceAdminServiceStub.delete(servicePath +
                    wsdlNamespacePath + serviceName);

            //check if the deleted file exists in registry
            if (!isResourceExist(sessionCookie, servicePath +
                    wsdlNamespacePath, serviceName, resourceAdminServiceStub)) {
                log.info("Resource successfully deleted from the registry");

            } else {
                log.error("Resource not deleted from the registry");
                Assert.fail("Resource not deleted from the registry");
            }
        } catch (Exception e) {
            Assert.fail("Unable to get text content " + e);
            log.error(" : " + e.getMessage());
        }
    }

    @Override
    public void runFailureCase() {

    }

    @Override
    public void cleanup() {

    }

    public static String fileReader(String fileName) {
        String fileContent = "";
        try {
            // Open the file that is the first
            // command line parameter
            FileInputStream fstream = new
                    FileInputStream(fileName);

            // Convert our input stream to a
            // DataInputStream
            DataInputStream in =
                    new DataInputStream(fstream);

            // Continue to read lines while
            // there are still some left to read

            while (in.available() != 0) {
                fileContent = fileContent + (in.readLine());
            }

            in.close();
        } catch (Exception e) {
            System.err.println("File input error");
        }
        return fileContent;

    }
}

