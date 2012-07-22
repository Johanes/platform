/*
 * Copyright 2004,2005 The Apache Software Foundation.
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

package org.wso2.carbon.registry.governance.api.test;

import java.rmi.RemoteException;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.automation.api.clients.governance.LifeCycleAdminServiceClient;
import org.wso2.carbon.automation.api.clients.governance.LifeCycleManagementClient;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.core.utils.UserInfo;
import org.wso2.carbon.automation.core.utils.UserListCsvReader;
import org.wso2.carbon.automation.core.utils.environmentutils.EnvironmentBuilder;
import org.wso2.carbon.automation.core.utils.environmentutils.ManageEnvironment;
import org.wso2.carbon.automation.utils.registry.RegistryProviderUtil;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.services.ServiceFilter;
import org.wso2.carbon.governance.api.services.ServiceManager;
import org.wso2.carbon.governance.api.services.dataobjects.Service;
import org.wso2.carbon.governance.api.wsdls.WsdlManager;
import org.wso2.carbon.governance.api.wsdls.dataobjects.Wsdl;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.services.ArrayOfString;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceExceptionException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

public class MetaDataServicesTestCase {

    private static final Log log = LogFactory.getLog(MetaDataServicesTestCase.class);
    private Registry governance;
    int userId = 1;
    Service newService;
    private final static String WSDL_URL = "https://svn.wso2.org/repos/wso2/carbon/platform/trunk/" +
                                           "platform-integration/clarity-tests/org.wso2.carbon.automation.test.repo/" +
                                           "src/main/resources/artifacts/GREG/wsdl/info.wsdl";
    ServiceManager serviceManager;
    private LifeCycleManagementClient lifeCycleManagerAdminService;
    private LifeCycleAdminServiceClient lifeCycleAdminService;
    private final String ACTION_PROMOTE = "Promote";
    private final String ACTION_ITEM_CLICK = "itemClick";
    private Service infoService;
    private Service infoServiceTesting;
    private Wsdl wsdl;

    @BeforeClass()
    public void initialize()
            throws RemoteException, LoginAuthenticationExceptionException, RegistryException {
        UserInfo userInfo = UserListCsvReader.getUserInfo(userId);
        WSRegistryServiceClient wsRegistry = new RegistryProviderUtil().getWSRegistry(userId, ProductConstant.GREG_SERVER_NAME);
        governance = new RegistryProviderUtil().getGovernanceRegistry(wsRegistry, userId);
        serviceManager = new ServiceManager(governance);
        EnvironmentBuilder builder = new EnvironmentBuilder().greg(userId);
        ManageEnvironment environment = builder.build();
        lifeCycleManagerAdminService = new LifeCycleManagementClient(environment.getGreg().getProductVariables().getBackendUrl(), userInfo.getUserName(), userInfo.getPassword());
        lifeCycleAdminService = new LifeCycleAdminServiceClient(environment.getGreg().getProductVariables().getBackendUrl(), userInfo.getUserName(), userInfo.getPassword());
    }

    /**
     * Add a service without the defaultServiceVersion property so that the service is saved as version
     * 1.0.0-SNAPSHOT
     *
     * @throws Exception
     */
    @Test(groups = {"wso2.greg"}, description = "service without the defaultServiceVersion property", priority = 1)
    public void addServiceWithoutVersion() throws Exception {

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep", "MyService"));
        serviceManager.addService(service);
        String serviceId = service.getId();
        newService = serviceManager.getService(serviceId);
        Assert.assertEquals(newService.getAttribute("overview_version"), "1.0.0-SNAPSHOT");
    }

    /**
     * Open an existing service, do changes to the service content and save. Verify whether the changes get persisted
     *
     * @throws Exception
     */
    @Test(groups = {"wso2.greg"}, description = "service without the defaultServiceVersion property", dependsOnMethods = "addServiceWithoutVersion", priority = 2)
    public void serviceDetailUpdateTest() throws Exception {
        newService.setQName(new QName("http://bang.boom.com/renamed", "MyServiceRenamed"));
        WsdlManager manager = new WsdlManager(governance);
        wsdl = manager.newWsdl(WSDL_URL);
        manager.addWsdl(wsdl);
        newService.attachWSDL(wsdl);
        serviceManager.updateService(newService);
        String serviceId = newService.getId();
        Assert.assertEquals(serviceManager.getService(serviceId).getQName().getLocalPart(), "MyServiceRenamed");
        Assert.assertEquals(serviceManager.getService(serviceId).getQName().getNamespaceURI(), "http://bang.boom.com/renamed");
        Assert.assertEquals(serviceManager.getService(serviceId).getAttachedWsdls()[0].getQName().getNamespaceURI(), "http://footballpool.dataaccess.eu");

    }


    /**
     * Change the default location where you want to add services and verify whether the service gets created at the correct location
     *
     * @throws GovernanceException
     */
    @Test(groups = {"wso2.greg"}, description = "Change the default location of a service", dependsOnMethods = "addServiceWithoutVersion", priority = 3)
    public void changeLocationTest() throws GovernanceException {
//		String servicePath = newService.getPath();
//		servicePath = servicePath+"/test";

    }

    /**
     * Update a service that is at branch level to verify whether the changes done do not get persisted to the trunk level service
     * <p/>
     * Set an LC to a service, then promote it to the next LC level. Then do changes to the service content and update the service and make sure that the LC state is not set back to it's initial state
     *
     * @throws LifeCycleManagementServiceExceptionException
     *
     * @throws RemoteException
     * @throws RegistryException
     * @throws CustomLifecyclesChecklistAdminServiceExceptionException
     *
     */
    @Test(groups = {"wso2.greg"}, description = "Update a service that is at trunk level", dependsOnMethods = "serviceDetailUpdateTest", priority = 4)
    public void changesAtBranchTest()
            throws RemoteException, LifeCycleManagementServiceExceptionException, RegistryException,
                   CustomLifecyclesChecklistAdminServiceExceptionException {
        ArrayOfString[] parameters = new ArrayOfString[2];

        infoService = serviceManager.findServices(new ServiceFilter() {

            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("overview_name");
                if (attributeVal != null && attributeVal.startsWith("Info")) {
                    return true;
                }
                return false;
            }
        })[0];
        infoService.attachLifecycle(lifeCycleManagerAdminService.getLifecycleList()[0]);

        String servicePathDev = "/_system/governance" + infoService.getPath();
        parameters[0] = new ArrayOfString();
        parameters[0].setArray(new String[]{servicePathDev, "2.0.0"});
        lifeCycleAdminService.invokeAspectWithParams(servicePathDev, "ServiceLifeCycle", ACTION_PROMOTE, null, parameters);

        infoServiceTesting = serviceManager.findServices(new ServiceFilter() {
            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("overview_name");
                String attributeVal2 = service.getAttribute("overview_version");
                if (attributeVal != null && attributeVal.startsWith("Info") && attributeVal2.startsWith("2.0.0")) {
                    return true;
                }
                return false;
            }
        })[0];
        infoServiceTesting.setAttribute("test-att", "test-value");
        Assert.assertEquals(infoService.getAttribute("test-att"), null);
        Assert.assertEquals(infoServiceTesting.getAttribute("test-att"), "test-value");
        Assert.assertEquals(infoServiceTesting.getLifecycleState(), "Testing");
    }

    /**
     * Update a service that is at trunk level to verify whether the changes done do not get persisted to the branch level services, that were promoted from the updated service
     *
     * @throws GovernanceException
     */
    @Test(groups = {"wso2.greg"}, description = "Update a service that is at trunk level", dependsOnMethods = "changesAtBranchTest", priority = 4)
    public void changesAtTrunkTest() throws GovernanceException {
        infoService.setAttribute("test-att2", "test-value");
        Assert.assertEquals(infoServiceTesting.getAttribute("test-att2"), null);
        Assert.assertEquals(infoService.getAttribute("test-att2"), "test-value");
    }


    /**
     * Create a service without a WSDL. Then add the WSDL later on and verify whether the dependencies get resolved
     *
     * @throws GovernanceException
     */
    @Test(groups = {"wso2.greg"}, description = "Create a service without a WSDL and verify dependencies", dependsOnMethods = "changesAtTrunkTest")
    public void verifyDependenciesTest() throws GovernanceException {
        Service serviceForDependencyVerification = serviceManager.newService(new QName("http://service.dependency.varification/mnm/beep", "serviceForDependencyVarification"));
        serviceManager.addService(serviceForDependencyVerification);
        serviceForDependencyVerification.attachWSDL(wsdl);
        Assert.assertEquals(serviceForDependencyVerification.getDependencies()[0].getQName().getLocalPart(), "info.wsdl");
    }

    /**
     * Delete a service that is in the trunk level and verify whether there is no effect on other services promoted from the deleted service
     *
     * @throws GovernanceException
     * @throws CustomLifecyclesChecklistAdminServiceExceptionException
     *
     * @throws RemoteException
     * @throws LifeCycleManagementServiceExceptionException
     *
     */
    @Test(groups = {"wso2.greg"}, description = "delete a service at trunk level", dependsOnMethods = "changesAtTrunkTest")
    public void deleteServiceAtTrunkTest() throws GovernanceException, RemoteException,
                                                  CustomLifecyclesChecklistAdminServiceExceptionException,
                                                  LifeCycleManagementServiceExceptionException {
        Service serviceForTrunkDeleteTest = serviceManager.newService(new QName("http://service.delete.trunk/mnm/beep", "serviceForTrunkDeleteTest"));
        serviceManager.addService(serviceForTrunkDeleteTest);
        String servicePathDev = "/_system/governance" + serviceForTrunkDeleteTest.getPath();
        ArrayOfString[] parameters = new ArrayOfString[2];
        parameters[0] = new ArrayOfString();
        parameters[0].setArray(new String[]{servicePathDev, "2.0.0"});
        serviceForTrunkDeleteTest.attachLifecycle(lifeCycleManagerAdminService.getLifecycleList()[0]);
        lifeCycleAdminService.invokeAspectWithParams(servicePathDev, "ServiceLifeCycle", ACTION_PROMOTE, null, parameters);
        Service serviceForTrunkDeleteTestPromoted = serviceManager.findServices(new ServiceFilter() {
            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("overview_name");
                String attributeVal2 = service.getAttribute("overview_version");
                if (attributeVal != null && attributeVal.startsWith("serviceForTrunkDeleteTest") && attributeVal2.startsWith("2.0.0")) {
                    return true;
                }
                return false;
            }
        })[0];
        serviceManager.removeService(serviceForTrunkDeleteTest.getId());
        Assert.assertEquals(serviceForTrunkDeleteTestPromoted.getPath(), "/branches/testing/services/trunk/delete/service/mnm/beep/2.0.0/serviceForTrunkDeleteTest");
    }

    /**
     *  Delete a service that is in the branch level and verify whether it has no impact to the service in the trunk level
     * @throws GovernanceException
     * @throws RemoteException
     * @throws CustomLifecyclesChecklistAdminServiceExceptionException
     * @throws LifeCycleManagementServiceExceptionException
     */
    @Test(groups = {"wso2.greg"}, description = "delete a service at trunk level", dependsOnMethods = "changesAtTrunkTest")
    public void deleteServiceAtBranchTest() throws GovernanceException, RemoteException,
                                                   CustomLifecyclesChecklistAdminServiceExceptionException,
                                                   LifeCycleManagementServiceExceptionException {
        Service serviceForBranchDeleteTest = serviceManager.newService(new QName("http://service.delete.branch/mnm/beep", "serviceForBranchDeleteTest"));
        serviceManager.addService(serviceForBranchDeleteTest);
        String servicePathDev = "/_system/governance" + serviceForBranchDeleteTest.getPath();
        ArrayOfString[] parameters = new ArrayOfString[2];
        parameters[0] = new ArrayOfString();
        parameters[0].setArray(new String[]{servicePathDev, "2.0.0"});
        serviceForBranchDeleteTest.attachLifecycle(lifeCycleManagerAdminService.getLifecycleList()[0]);
        lifeCycleAdminService.invokeAspectWithParams(servicePathDev, "ServiceLifeCycle", ACTION_PROMOTE, null, parameters);
        Service serviceForBranchDeleteTestPromoted = serviceManager.findServices(new ServiceFilter() {
            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("overview_name");
                String attributeVal2 = service.getAttribute("overview_version");
                return attributeVal != null && attributeVal.startsWith("serviceForBranchDeleteTest") && attributeVal2.startsWith("2.0.0");
            }
        })[0];
        serviceManager.removeService(serviceForBranchDeleteTestPromoted.getId());
        Assert.assertEquals(serviceForBranchDeleteTest.getPath(), "/trunk/services/branch/delete/service/mnm/beep/serviceForBranchDeleteTest");
    }
}