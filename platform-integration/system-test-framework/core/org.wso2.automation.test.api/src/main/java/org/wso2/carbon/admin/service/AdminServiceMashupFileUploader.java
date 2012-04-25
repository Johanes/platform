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
import org.wso2.carbon.mashup.jsservices.stub.client.types.JSServiceUploadData;
import org.wso2.carbon.mashup.jsservices.stub.fileupload.ExceptionException;
import org.wso2.carbon.mashup.jsservices.stub.fileupload.JSServiceUploaderStub;

import javax.activation.DataHandler;
import java.rmi.RemoteException;


public class AdminServiceMashupFileUploader {
    private static final Log log = LogFactory.getLog(AdminServiceMashupFileUploader.class);

    private JSServiceUploaderStub jSServiceUploaderStub;

    public AdminServiceMashupFileUploader(String backEndUrl) throws AxisFault {
        String serviceName = "JSServiceUploader";
        String endPoint = backEndUrl + serviceName;
        jSServiceUploaderStub = new JSServiceUploaderStub(endPoint);
    }

    public void uploadMashUpFile(String sessionCookie, String fileName, DataHandler dh) throws ExceptionException, RemoteException {

        JSServiceUploadData jSSFile;

        AuthenticateStub.authenticateStub(sessionCookie, jSServiceUploaderStub);

        jSSFile = new JSServiceUploadData();
        jSSFile.setDataHandler(dh);
        jSSFile.setFileName(fileName);

        jSServiceUploaderStub.uploadService("admin", new JSServiceUploadData[]{jSSFile});
        log.info("Artifact uploaded");

    }
}
