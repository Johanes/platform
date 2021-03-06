/*
*  Copyright (c) WSO2 Inc. (http://wso2.com) All Rights Reserved.
 
  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*
*/


package org.wso2.carbon.registry.resource.test;

import static org.testng.Assert.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.integration.core.TestTemplate;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.registry.relations.stub.RelationAdminServiceStub;
import org.wso2.carbon.registry.relations.stub.beans.xsd.AssociationTreeBean;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;
import org.wso2.carbon.registry.resource.stub.beans.xsd.CollectionContentBean;

import javax.activation.DataHandler;
import java.io.File;
import java.net.URL;

/**
 * A test case which tests dependency feature validate operation
 */
public class DependencyTestCase {
    /**
     * @goal testing dependency feature in registry
     */

    private static final Log log = LogFactory.getLog(DependencyTestCase.class);
    private String loggedInSessionCookie = "";
    private LoginLogoutUtil util = new LoginLogoutUtil();
    private String frameworkPath = "";

    @BeforeClass(groups = {"wso2.greg"})
    public void init() throws Exception {
        loggedInSessionCookie = util.login();
        frameworkPath = FrameworkSettings.getFrameworkPath();

    }

    @Test(groups = {"wso2.greg"})
    public void runSuccessCase() {
        log.debug("Running SuccessCase");

        try {

            ResourceAdminServiceStub resourceAdminServiceStub = TestUtils.getResourceAdminServiceStub(loggedInSessionCookie);
            RelationAdminServiceStub relationAdminServiceStub = TestUtils.getRelationAdminServiceStub(loggedInSessionCookie);
            CollectionContentBean collectionContentBean = new CollectionContentBean();
            collectionContentBean = resourceAdminServiceStub.getCollectionContent("/");
            if (collectionContentBean.getChildCount() > 0) {
                String[] childPath = collectionContentBean.getChildPaths();
                for (int i = 0; i <= childPath.length - 1; i++) {
                    if (childPath[i].equalsIgnoreCase("/TestAutomation")) {
                        resourceAdminServiceStub.delete("/TestAutomation");
                    }
                }
            }
            String collectionPath = resourceAdminServiceStub.addCollection("/", "TestAutomation", "", "");
            log.info("collection added to " + collectionPath);
            collectionPath = resourceAdminServiceStub.addCollection("/TestAutomation", "communityTest", "", "");
            log.info("collection added to " + collectionPath);
            collectionPath = resourceAdminServiceStub.addCollection("/TestAutomation/communityTest",
                    "TestCollection1", "", "");
            collectionPath = resourceAdminServiceStub.addCollection("/TestAutomation/communityTest",
                    "TestCollection2", "", "");
            String resource = frameworkPath + File.separator + ".." + File.separator + ".." + File.separator + ".." +
                    File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator +
                    "resources" + File.separator + "sampleText.txt";
            resourceAdminServiceStub.addResource("/TestAutomation/communityTest/TestCollection1/sampleText.txt",
                    "text/html", "txtDesc", new DataHandler(new URL("file:///" + resource)), null, null);
            resourceAdminServiceStub.getTextContent("/TestAutomation/communityTest/TestCollection1/sampleText.txt");
            addDependency(relationAdminServiceStub);
            deleteDependency(relationAdminServiceStub);
//            addInvalidDependency(relationAdminServiceStub);


            resourceAdminServiceStub.delete("/TestAutomation");
        } catch (Exception e) {
            fail("error occured while running dependency test " + e);
            log.error(" error occured while running dependency test " + e.getMessage());

        }
    }

    private void addDependency(RelationAdminServiceStub relationAdminServiceStub) {
        try {
            relationAdminServiceStub.addAssociation("/TestAutomation/communityTest/TestCollection2", "depends",
                    "/TestAutomation/communityTest/TestCollection1/sampleText.txt", "add");
            AssociationTreeBean associationTreeBean = relationAdminServiceStub.getAssociationTree(
                    "/TestAutomation/communityTest/TestCollection2", "depends");
            if (!associationTreeBean.getAssociationTree().contains(
                    "/TestAutomation/communityTest/TestCollection1/sampleText.txt")) {
                log.error("Added dependency not found in /TestAutomation/communityTest/TestCollection2");
                fail("Added dependency not found in /TestAutomation/communityTest/TestCollection2");
            }
            log.debug("associationTreeBean : " + associationTreeBean.getAssociationTree());
        } catch (Exception e) {
            log.error("Unable to add dependency in /TestAutomation/communityTest/TestCollection2");
            fail("Unable to add dependency in /TestAutomation/communityTest/TestCollection2");

        }
    }

    private void deleteDependency(RelationAdminServiceStub relationAdminServiceStub) {
        try {
            relationAdminServiceStub.addAssociation("/TestAutomation/communityTest/TestCollection2", "depends",
                    "/TestAutomation/communityTest/TestCollection1/sampleText.txt", "remove");
            log.debug("dependency removed in : /TestAutomation/communityTest/TestCollection2");
        } catch (Exception e) {
            log.error("Unable to remove dependency in /TestAutomation/communityTest/TestCollection2");
            fail("Unable to remove dependency in /TestAutomation/communityTest/TestCollection2");
        }
    }

    public void addInvalidDependency(RelationAdminServiceStub relationAdminServiceStub) {
        // current release havin bug = CARBON-8272
        try {
            relationAdminServiceStub.addAssociation("/TestAutomation/communityTest/TestCollection2", "depends",
                    "/TestAutomation/communityTest/TestCollection1/invalid", "add");
            log.error("Error: Expected exception not thrown while adding invalid dependecy path");
            fail("Error: Expected exception not thrown while adding invalid dependecy path");
        } catch (Exception e) {
            //toDo exception need to check here
            // ToDo http and https url need to add
        }
    }

}
