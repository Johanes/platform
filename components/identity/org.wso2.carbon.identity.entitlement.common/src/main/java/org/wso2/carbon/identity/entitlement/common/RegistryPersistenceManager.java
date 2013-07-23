/*
*  Copyright (c)  WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.identity.entitlement.common;

import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.registry.api.RegistryException;
import org.wso2.carbon.registry.api.Resource;
import org.wso2.carbon.registry.api.Registry;

import java.nio.charset.Charset;

/**
 *
 */
public class RegistryPersistenceManager extends InMemoryPersistenceManager{

    @Override
    public void persistConfig(String xmlConfig) throws PolicyEditorException {

        super.persistConfig(xmlConfig);

        Registry registry = CarbonContext.getCurrentContext().getRegistry(RegistryType.SYSTEM_GOVERNANCE);
        try{
            Resource resource = registry.newResource();
            resource.setContent(xmlConfig);
            registry.put(EntitlementConstants.ENTITLEMENT_POLICY_EDITOR_CONFIG_FILE_REGISTRY_PATH,
                    resource);
        } catch (RegistryException e) {
            throw new PolicyEditorException("Error while persisting policy editor config");
        }
    }

    @Override
    public String getConfig() {
        String config = super.getConfig();
        if(config == null){
            Registry registry = CarbonContext.getCurrentContext().getRegistry(RegistryType.SYSTEM_GOVERNANCE);
            try{
                Resource resource = registry.
                        get(EntitlementConstants.ENTITLEMENT_POLICY_EDITOR_CONFIG_FILE_REGISTRY_PATH);
                if(resource != null && resource.getContent() != null){
                    config =  new String((byte[]) resource.getContent(), Charset.forName("UTF-8"));
                }
            } catch (RegistryException e) {
                // ignore and load default config
            }
        }

        if(config == null || config.trim().length() == 0){
            config = getDefaultConfig();
        }
        return config;
    }
}
