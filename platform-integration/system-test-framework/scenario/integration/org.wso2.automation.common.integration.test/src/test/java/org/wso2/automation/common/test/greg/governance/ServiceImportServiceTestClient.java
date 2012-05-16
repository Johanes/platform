/*
* Copyright (c) 2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.wso2.automation.common.test.greg.governance;


import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.*;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.services.ServiceManager;
import org.wso2.carbon.governance.api.services.dataobjects.Service;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;
import org.wso2.platform.test.core.ProductConstant;
import org.wso2.platform.test.core.utils.gregutils.GregUserIDEvaluator;
import org.wso2.platform.test.core.utils.gregutils.RegistryProvider;

import static org.testng.Assert.*;

import javax.xml.namespace.QName;


public class ServiceImportServiceTestClient {
    private static final Log log = LogFactory.getLog(SchemaImportServiceTestClient.class);
    private static WSRegistryServiceClient registry = null;
    private static Registry governance = null;


    @BeforeClass(alwaysRun = true)
    public void init() throws RegistryException, AxisFault {
        int userId = new GregUserIDEvaluator().getTenantID();
        registry = new RegistryProvider().getRegistry(userId, ProductConstant.GREG_SERVER_NAME);
        governance = new RegistryProvider().getGovernance(registry, userId);
        //Delete Service already exists
        deleteService();
    }

    @Test(groups = {"wso2.greg"}, description = "add a simple service", priority = 1)
    private void testAddService() throws RegistryException {
        String service_namespace = "http://example.com/demo/services";
        String service_name = "ExampleService";
        String service_path = "/_system/governance/trunk/services/com/example/demo/services/ExampleService";

        createService(service_namespace, service_name);

        try {
            //Assert Service exists
            assertTrue(registry.resourceExists(service_path), "Service Exists");
            //Remove Service
            registry.delete(service_path);
            //Assert Service removed successfully
            assertFalse(registry.resourceExists(service_path), "Service Exists");
            log.info("ServiceImportServiceTestClient testAddService() - Passed");
        } catch (RegistryException e) {
            log.error("Failed testAddService() test :" + e);
            throw new RegistryException("Failed testAddService() test :" + e);
        }
    }

    public Service createService(String service_namespace, String service_name) throws GovernanceException {
        ServiceManager serviceManager = new ServiceManager(governance);
        Service service;
        try {
            service = serviceManager.newService(new QName(service_namespace, service_name));
            service.addAttribute("creator", "Aaaa");
            service.addAttribute("version", "1.0.0");
            serviceManager.addService(service);
            log.info("Service Added Successfully");
        } catch (GovernanceException e) {
            log.error("Failed to add Service:" + e);
            throw new GovernanceException("Failed to add Service:" + e);
        }
        return service;
    }

    public void deleteService() throws RegistryException {
        try {
            if (registry.resourceExists("/_system/governance/trunk/services/com/example/demo/services/ExampleService")) {
                registry.delete("/_system/governance/trunk/services/com/example/demo/services/ExampleService");
            }
        } catch (RegistryException e) {
            log.error("Failed to Delete Service :" + e);
            throw new RegistryException("Failed to Delete Service :" + e);
        }
    }

}
