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
package org.wso2.carbon.mediator.autoscale.ec2autoscale.mediators;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.wso2.carbon.lb.common.conf.LoadBalancerConfiguration;

import javax.xml.namespace.QName;
import java.util.Properties;

/**
 *
 */
@SuppressWarnings("unused")
public class AutoscaleInMediatorFactory extends AbstractMediatorFactory {

    private static final QName AUTOSCALE_IN_QNAME = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                              "autoscaleIn");

    public Mediator createSpecificMediator(OMElement omElement, Properties properties) {
        AutoscaleInMediator autoscaleInMediator = new AutoscaleInMediator();
        String configuration = omElement.getAttributeValue(new QName("configuration"));
        if (configuration == null || configuration.isEmpty()) {
            throw new SynapseException("configuration attribute of autoscaleIn mediator cannot be empty");
        }
        if (configuration.startsWith("$system:")) {
            configuration = System.getProperty(configuration.substring("$system:".length()));
        }
        LoadBalancerConfiguration LBConfig = new LoadBalancerConfiguration();
        LBConfig.init(configuration);
        autoscaleInMediator.setLBConfig(LBConfig);
        autoscaleInMediator.setConfiguration(configuration);
        return autoscaleInMediator;
    }

    public QName getTagQName() {
        return AUTOSCALE_IN_QNAME;
    }
}
