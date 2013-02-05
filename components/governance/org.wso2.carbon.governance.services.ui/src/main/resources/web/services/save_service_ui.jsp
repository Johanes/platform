<!--
 ~ Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 ~
 ~ WSO2 Inc. licenses this file to you under the Apache License,
 ~ Version 2.0 (the "License"); you may not use this file except
 ~ in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~    http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 -->
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="org.wso2.carbon.registry.common.ui.UIException" %>
<%@ page import="org.wso2.carbon.registry.core.RegistryConstants" %>
<%@ page import="org.wso2.carbon.governance.services.ui.utils.AddServicesUtil" %>
<%@ page import="org.wso2.carbon.ui.CarbonUIMessage" %>
<%@ page import="org.wso2.carbon.registry.extensions.utils.CommonUtil" %>
<%@ page import="org.wso2.carbon.governance.services.ui.utils.AddServiceUIGenerator" %>
<%@ page import="org.apache.axiom.om.OMElement" %>
<%@ page import="org.wso2.carbon.governance.services.ui.clients.AddServicesServiceClient" %>
<%@ page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%
    String error = "Wrong Configuration,please refer the default configuration in repository/resources/services-conf.xml";
    String update = request.getParameter("payload");
    AddServicesServiceClient client = new AddServicesServiceClient(config,session);
    if(client.saveServiceConfiguration(update)){
        //successfully saved the edited content//
        String path = "";
        if (CarbonUIUtil.isUserAuthorized(request,
                "/permission/admin/manage/resources/govern/metadata/add") &&
                CarbonUIUtil.isUserAuthorized(request,
                "/permission/admin/manage/resources/browse")) {
            path = "../services/services.jsp?region=region3&item=governance_services_menu";
        } else {
            path = "../services/configure.jsp?region=region1&item=governance_services_config_menu";
        }
       %>
<script type="text/javascript">
    location.href = "<%=path%>";
</script>
<%
    }
    else{
       %>
<script type="text/javascript">
    CARBON.showErrorDialog("<%=error%>", function() {
        location.href = "../services/configure.jsp?region=region1&item=governance_services_config_menu";
        return;
    });
</script>
           <%
       }

   %>



