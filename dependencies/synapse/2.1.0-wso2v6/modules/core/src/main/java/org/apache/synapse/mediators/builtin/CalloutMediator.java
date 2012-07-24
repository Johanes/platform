/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.mediators.builtin;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.synapse.*;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.util.MessageHelper;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;

import java.util.List;

/**
 * <callout serviceURL="string" [action="string"] [initAxis2ClientOptions="boolean"]>
 *      <configuration [axis2xml="string"] [repository="string"]/>?
 *      <source xpath="expression" | key="string"> <!-- key can be a MC property or entry key -->
 *      <target xpath="expression" | key="string"/>
 * </callout>
 */
public class CalloutMediator extends AbstractMediator implements ManagedLifecycle {

    private ConfigurationContext configCtx = null;
    private String serviceURL = null;
    private String action = null;
    private String requestKey = null;
    private SynapseXPath requestXPath = null;
    private SynapseXPath targetXPath = null;
    private String targetKey = null;
    private String clientRepository = null;
    private String axis2xml = null;
    private String useServerConfig = null;
    private boolean initClientOptions = true;
    public final static String DEFAULT_CLIENT_REPO = "./samples/axis2Client/client_repo";
    public final static String DEFAULT_AXIS2_XML = "./samples/axis2Client/client_repo/conf/axis2.xml";

    public boolean mediate(MessageContext synCtx) {

        SynapseLog synLog = getLog(synCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Callout mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        try {
            ServiceClient sc = new ServiceClient(configCtx, null);

            Options options;
            if (initClientOptions) {
                options = new Options();
            } else {
                org.apache.axis2.context.MessageContext axis2MessageCtx =
                        ((Axis2MessageContext) synCtx).getAxis2MessageContext();
                options = axis2MessageCtx.getOptions();
            }

            options.setTo(new EndpointReference(serviceURL));

            if (action != null) {
                options.setAction(action);
            } else {
                if (synCtx.isSOAP11()) {
                    options.setProperty(Constants.Configuration.DISABLE_SOAP_ACTION, true);
                } else {
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    axis2MessageCtx.getTransportOut().addParameter(
                            new Parameter(HTTPConstants.OMIT_SOAP_12_ACTION, true));
                }
            }

            options.setProperty(
                    AddressingConstants.DISABLE_ADDRESSING_FOR_OUT_MESSAGES, Boolean.TRUE);
            sc.setOptions(options);

            OMElement request = getRequestPayload(synCtx);
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("About to invoke service : " + serviceURL + (action != null ?
                    " with action : " + action : ""));
                if (synLog.isTraceTraceEnabled()) {
                    synLog.traceTrace("Request message payload : " + request);
                }
            }

            OMElement result = null;
            try {
                options.setCallTransportCleanup(true);
                result = sc.sendReceive(request);
            } catch (AxisFault axisFault) {
                handleFault(synCtx, axisFault);
            }

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Response payload received : " + result);
            }

            if (result != null) {
                if (targetXPath != null) {
                    Object o = targetXPath.evaluate(synCtx);

                    if (o != null && o instanceof OMElement) {
                        OMNode tgtNode = (OMElement) o;
                        tgtNode.insertSiblingAfter(result);
                        tgtNode.detach();
                    } else if (o != null && o instanceof List && !((List) o).isEmpty()) {
                        // Always fetches *only* the first
                        OMNode tgtNode = (OMElement) ((List) o).get(0);
                        tgtNode.insertSiblingAfter(result);
                        tgtNode.detach();
                    } else {
                        handleException("Evaluation of target XPath expression : " +
                            targetXPath.toString() + " did not yeild an OMNode", synCtx);
                    }
                } if (targetKey != null) {
                    synCtx.setProperty(targetKey, result);
                }
            } else {
                synLog.traceOrDebug("Service returned a null response");
            }

        } catch (AxisFault e) {
            handleException("Error invoking service : " + serviceURL +
                (action != null ? " with action : " + action : ""), e, synCtx);
        } catch (JaxenException e) {
            handleException("Error while evaluating the XPath expression: " + targetXPath,
                    e, synCtx);
        }

        synLog.traceOrDebug("End : Callout mediator");
        return true;
    }

    private void handleFault(MessageContext synCtx, AxisFault axisFault) {
        synCtx.setProperty(SynapseConstants.SENDING_FAULT, Boolean.TRUE);
        if (axisFault.getFaultCodeElement() != null) {
            synCtx.setProperty(SynapseConstants.ERROR_CODE,
                    axisFault.getFaultCodeElement().getText());
        } else {
            synCtx.setProperty(SynapseConstants.ERROR_CODE,
                    SynapseConstants.CALLOUT_OPERATION_FAILED);
        }

        if (axisFault.getFaultReasonElement() != null) {
            synCtx.setProperty(SynapseConstants.ERROR_MESSAGE,
                    axisFault.getFaultReasonElement().getText());
        } else {
            synCtx.setProperty(SynapseConstants.ERROR_MESSAGE, "Error while performing " +
                    "the callout operation");
        }

        if (axisFault.getFaultDetailElement() != null) {
            if (axisFault.getFaultDetailElement().getFirstElement() != null) {
                synCtx.setProperty(SynapseConstants.ERROR_DETAIL,
                        axisFault.getFaultDetailElement().getFirstElement());
            } else {
                synCtx.setProperty(SynapseConstants.ERROR_DETAIL,
                        axisFault.getFaultDetailElement().getText());
            }
        }

        synCtx.setProperty(SynapseConstants.ERROR_EXCEPTION, axisFault);
        throw new SynapseException("Error while performing the callout operation", axisFault);
    }

    private OMElement getRequestPayload(MessageContext synCtx) throws AxisFault {

        if (requestKey != null) {
            Object request = synCtx.getProperty(requestKey);
            if (request == null) {
                request = synCtx.getEntry(requestKey);
            }
            if (request != null && request instanceof OMElement) {
                return (OMElement) request;
            } else {
                handleException("The property : " + requestKey + " is not an OMElement", synCtx);
            }
        } else if (requestXPath != null) {
            try {
                Object o = requestXPath.evaluate(MessageHelper.cloneMessageContext(synCtx));

                if (o instanceof OMElement) {
                    return (OMElement) o;
                } else if (o instanceof List && !((List) o).isEmpty()) {
                    return (OMElement) ((List) o).get(0);  // Always fetches *only* the first
                } else {
                    handleException("The evaluation of the XPath expression : "
                        + requestXPath.toString() + " did not result in an OMElement", synCtx);
                }
            } catch (JaxenException e) {
                handleException("Error evaluating XPath expression : "
                        + requestXPath.toString(), e, synCtx);
            }
        }
        return null;
    }

    public void init(SynapseEnvironment synEnv) {
        try {
        	if (Boolean.parseBoolean(useServerConfig)) {
        		configCtx = ((Axis2SynapseEnvironment) synEnv).getAxis2ConfigurationContext();
        	} else {
        		configCtx = ConfigurationContextFactory.createConfigurationContextFromFileSystem(
                        clientRepository != null ? clientRepository : DEFAULT_CLIENT_REPO,
                axis2xml != null ? axis2xml : DEFAULT_AXIS2_XML);
        	}
         } catch (AxisFault e) {
            String msg = "Error initializing callout mediator : " + e.getMessage();
            log.error(msg, e);
            throw new SynapseException(msg, e);
        }
    }

    public void destroy() {
        try {
            configCtx.terminate();
        } catch (AxisFault ignore) {}
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
    
    public String getUseServerConfig() {
    	return useServerConfig;
    }
    
    public void setUseServerConfig(String useServerConfig) {
    	this.useServerConfig = useServerConfig;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public void setRequestXPath(SynapseXPath requestXPath) throws JaxenException {
        this.requestXPath = requestXPath;
    }

    public void setTargetXPath(SynapseXPath targetXPath) throws JaxenException {
        this.targetXPath = targetXPath;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public SynapseXPath getRequestXPath() {
        return requestXPath;
    }

    public SynapseXPath getTargetXPath() {
        return targetXPath;
    }

    public String getClientRepository() {
        return clientRepository;
    }

    public void setClientRepository(String clientRepository) {
        this.clientRepository = clientRepository;
    }

    public String getAxis2xml() {
        return axis2xml;
    }

    public void setAxis2xml(String axis2xml) {
        this.axis2xml = axis2xml;
    }

    public boolean getInitClientOptions() {
        return initClientOptions;
    }

    public void setInitClientOptions(boolean initClientOptions) {
        this.initClientOptions = initClientOptions;
    }
}
