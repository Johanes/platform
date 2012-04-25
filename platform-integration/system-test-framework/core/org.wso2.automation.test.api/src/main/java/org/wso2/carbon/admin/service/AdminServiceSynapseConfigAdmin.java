/**
 *  Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
*ConfigServiceAdmin methods will be called using its returned stub
*/

package org.wso2.carbon.admin.service;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.admin.service.utils.AuthenticateStub;
import org.wso2.carbon.mediation.configadmin.stub.ConfigServiceAdminStub;
import org.wso2.carbon.mediation.configadmin.stub.types.carbon.ConfigurationInformation;
import org.wso2.carbon.mediation.configadmin.stub.types.carbon.ValidationError;

import javax.servlet.ServletException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.lang.String;
import java.rmi.RemoteException;

/**
 * This class exposing ConfigServiceAdmin operations to the test cases.
 */
public class AdminServiceSynapseConfigAdmin {


    private static final Log log = LogFactory.getLog(AdminServiceSynapseConfigAdmin.class);

    private ConfigServiceAdminStub configServiceAdminStub;

    public AdminServiceSynapseConfigAdmin(String sessionCookie, String backEndUrl)
            throws AxisFault {
        String serviceName = "ConfigServiceAdmin";
        String endPoint = backEndUrl + serviceName;
        log.debug("admin service url = " + endPoint);
        configServiceAdminStub = new ConfigServiceAdminStub(endPoint);
        AuthenticateStub.authenticateStub(sessionCookie, configServiceAdminStub);
    }

    /**
     * Activating service
     *
     * @param serviceName service name need to be activated
     * @throws RemoteException throwable exception
     */
    public void activateService(String serviceName) throws RemoteException {
        configServiceAdminStub.activate(serviceName);
    }

    /**
     * Adding more configuration to the existing service
     *
     * @param serviceName service name
     * @throws RemoteException throwable exception
     */
    public void addExistingConfiguration(String serviceName) throws RemoteException {
        configServiceAdminStub.addExistingConfiguration(serviceName);
    }

    /**
     * @param serviceName service name
     * @param description service description
     * @throws RemoteException throwable exception
     */
    public void create(String serviceName, String description) throws RemoteException {
        configServiceAdminStub.create(serviceName, description);
    }

    /**
     * Deleting synapse configuration
     *
     * @param serviceName service name
     * @throws RemoteException throwable exception
     */
    public void deleteConfiguration(String serviceName) throws RemoteException {
        configServiceAdminStub.deleteConfiguration(serviceName);
    }

    /**
     * Get current synapse configuration
     *
     * @return synapse configuration
     * @throws RemoteException throwable exception
     */
    public String getConfiguration() throws RemoteException {
        return configServiceAdminStub.getConfiguration();
    }

    /**
     * @return configuration list
     * @throws RemoteException throwable exception
     */
    public ConfigurationInformation[] getConfigurationList() throws RemoteException {
        return configServiceAdminStub.getConfigurationList();
    }

    /**
     * save synapse configuration
     *
     * @throws RemoteException throwable exception
     */
    public void saveConfigurationToDisk() throws RemoteException {
        configServiceAdminStub.saveConfigurationToDisk();
    }

    /**
     * update synapse configuration
     *
     * @param configuration synapse configuration
     * @return configuration update status
     * @throws java.rmi.RemoteException       throwable exception
     * @throws javax.servlet.ServletException throwable exception
     * @throws javax.xml.stream.XMLStreamException
     *                                        throwable exception
     */
    public boolean updateConfiguration(String configuration)
            throws XMLStreamException, ServletException, RemoteException {
        return configServiceAdminStub.updateConfiguration(createOMElement(configuration));
    }

    /**
     * Validate configuration
     *
     * @param configuration ynapse configuration
     * @return validation error array
     * @throws RemoteException throwable exception
     */
    public ValidationError[] validateConfiguration(OMElement configuration) throws RemoteException {
        return configServiceAdminStub.validateConfiguration(configuration);
    }

    private static OMElement createOMElement(String xml)
            throws ServletException, XMLStreamException {
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        StAXOMBuilder builder = new StAXOMBuilder(reader);
        return builder.getDocumentElement();

    }


}
