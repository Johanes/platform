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
import org.wso2.carbon.application.mgt.stub.ApplicationAdminExceptionException;
import org.wso2.carbon.application.mgt.stub.ApplicationAdminStub;
import org.wso2.carbon.application.mgt.stub.types.carbon.ApplicationMetadata;

import java.rmi.RemoteException;

public class AdminServiceApplicationAdmin {
    private static final Log log = LogFactory.getLog(AdminServiceApplicationAdmin.class);

    private ApplicationAdminStub applicationAdminStub;

    public AdminServiceApplicationAdmin(String backEndUrl) throws AxisFault {
        String serviceName = "ApplicationAdmin";
        String endPoint = backEndUrl + serviceName;
        applicationAdminStub = new ApplicationAdminStub(endPoint);
    }


    public void deleteApplication(String sessionCookie, String appName) throws ApplicationAdminExceptionException, RemoteException {
        new AuthenticateStub().authenticateStub(sessionCookie, applicationAdminStub);

        applicationAdminStub.deleteApplication(appName);
        log.info("Application Deleted");

    }

    public String[] listAllApplications(String sessionCookie) throws ApplicationAdminExceptionException, RemoteException {

        String[] appList;

        new AuthenticateStub().authenticateStub(sessionCookie, applicationAdminStub);
        appList = applicationAdminStub.listAllApplications();
        return appList;
    }

    public ApplicationMetadata getMetaData(String sessionCookie, String appName) throws ApplicationAdminExceptionException, RemoteException {

        ApplicationMetadata appList;

        new AuthenticateStub().authenticateStub(sessionCookie, applicationAdminStub);
        appList = applicationAdminStub.getAppData(appName);
        return appList;
    }

    public String[] deleteMatchingApplication(String sessionCookie, String appName) throws ApplicationAdminExceptionException, RemoteException {
        String[] appList;
        new AuthenticateStub().authenticateStub(sessionCookie, applicationAdminStub);
        appList = applicationAdminStub.listAllApplications();

        for (String anAppList : appList) {
            if (appName.contains(anAppList)) {
                applicationAdminStub.deleteApplication(anAppList);
            }
        }
        return appList;
    }


}
