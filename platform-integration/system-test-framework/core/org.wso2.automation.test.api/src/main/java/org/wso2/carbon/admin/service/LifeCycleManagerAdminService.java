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
package org.wso2.carbon.admin.service;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.admin.service.utils.AuthenticateStub;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceExceptionException;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceStub;
import sun.text.normalizer.ICUBinary;

import java.rmi.RemoteException;

public class LifeCycleManagerAdminService {
    private static final Log log = LogFactory.getLog(LifeCycleAdminService.class);

    private final String serviceName = "LifeCycleManagementService";
    private LifeCycleManagementServiceStub lifeCycleManagementServiceStub;
    private String endPoint;

    public LifeCycleManagerAdminService(String backEndUrl) throws AxisFault {
        this.endPoint = backEndUrl + serviceName;
        lifeCycleManagementServiceStub = new LifeCycleManagementServiceStub(endPoint);
    }

    public boolean addLifeCycle(String sessionCookie, String lifeCycleConfiguration)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.createLifecycle(lifeCycleConfiguration);
    }

    public boolean editLifeCycle(String sessionCookie, String oldLifeCycleName,
                                 String lifeCycleConfiguration)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.updateLifecycle(oldLifeCycleName, lifeCycleConfiguration);
    }

    public boolean deleteLifeCycle(String sessionCookie, String lifeCycleName)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.deleteLifecycle(lifeCycleName);
    }

    public String[] getLifecycleList(String sessionCookie)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.getLifecycleList();
    }

    public String getLifecycleConfiguration(String sessionCookie, String lifeCycleName)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.getLifecycleConfiguration(lifeCycleName);
    }

    public boolean isLifecycleNameInUse(String sessionCookie, String lifeCycleName)
            throws LifeCycleManagementServiceExceptionException, RemoteException {
        AuthenticateStub.authenticateStub(sessionCookie, lifeCycleManagementServiceStub);
        return lifeCycleManagementServiceStub.isLifecycleNameInUse(lifeCycleName);
    }
}
