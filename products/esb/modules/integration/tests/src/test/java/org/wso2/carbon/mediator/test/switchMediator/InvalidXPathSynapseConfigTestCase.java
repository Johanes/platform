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
package org.wso2.carbon.mediator.test.switchMediator;

import org.apache.axis2.AxisFault;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.mediator.test.ESBMediatorTest;

import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

public class InvalidXPathSynapseConfigTestCase extends ESBMediatorTest {

    @BeforeClass
    public void beforeClass() throws Exception {
        super.init();

    }

    @Test(groups = {"wso2.esb"}, description = "Saving Invalid XPath synapse configuration")
    public void uploadingInvalidXPathSynapse() throws Exception {
        String filePath = "/artifacts/ESB/synapseconfig/filters/switchMediator/Invalid_xpath.xml";
        try {

            loadESBConfigurationFromClasspath(filePath);
            fail("Synapse configuration get saved successfully. This Configuration should not be saved");
        } catch (AxisFault expected) {
            assertTrue("Error Message mismatched.not contain message 'Error while updating the Synapse configuration'", expected.getReason()
                    .contains("Error while updating the Synapse configuration"));
        }
    }

    @AfterClass
    public void afterClass() {
        super.cleanup();
    }

}