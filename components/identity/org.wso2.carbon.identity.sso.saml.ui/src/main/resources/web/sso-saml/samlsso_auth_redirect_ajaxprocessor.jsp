<!--
~ Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
~
~ WSO2 Inc. licenses this file to you under the Apache License,
~ Version 2.0 (the "License"); you may not use this file except
~ in compliance with the License.
~ You may obtain a copy of the License at
~
~ http://www.apache.org/licenses/LICENSE-2.0
~
~ Unless required by applicable law or agreed to in writing,
~ software distributed under the License is distributed on an
~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
~ KIND, either express or implied. See the License for the
~ specific language governing permissions and limitations
~ under the License.
-->
<%@page import="org.wso2.carbon.utils.multitenancy.MultitenantConstants" %>
<%@page import="org.wso2.carbon.utils.multitenancy.MultitenantUtils" %>
<%@page import="java.net.URLDecoder" %>
<%@page import="java.net.URLEncoder" %>
<%@page import="java.net.URL" %>
<%@page import="java.net.HttpURLConnection" %>
<%@page import="org.wso2.carbon.identity.sso.saml.ui.SAMLSSOProviderConstants" %>
<%@page import="java.util.List" %>
<%@page import="java.util.Enumeration" %>
<%@page import="org.apache.commons.codec.binary.Base64" %>
<%@page import="org.wso2.carbon.identity.core.util.IdentityUtil" %>
<%@page import="org.wso2.carbon.identity.base.IdentityConstants" %>
<%@page import="org.wso2.carbon.identity.sso.saml.ui.session.mgt.FESessionManager" %>
<%@page import="org.wso2.carbon.identity.sso.saml.ui.session.mgt.FESessionBean" %>
<%@page import="org.wso2.carbon.identity.sso.saml.stub.types.SAMLSSOReqValidationResponseDTO" %>
<%@page import="org.wso2.carbon.identity.sso.saml.stub.types.SAMLSSORespDTO" %>
<html>
<head></head>
<body>
<%
    String assertionConsumerURL = (String) request.getAttribute(SAMLSSOProviderConstants.ASSRTN_CONSUMER_URL);
%>
<p>You are now redirected back to <%=request.getAttribute(SAMLSSOProviderConstants.LOGIN_PAGE)%>. If the
   redirection fails, please click the post button.</p>

<form method="post" action="<%=request.getAttribute(SAMLSSOProviderConstants.LOGIN_PAGE)%>">
    <p>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.ASSRTN_CONSUMER_URL %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.ASSRTN_CONSUMER_URL) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.ISSUER %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.ISSUER) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.REQ_ID %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.REQ_ID) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.SUBJECT %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.SUBJECT) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.RP_SESSION_ID %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.RP_SESSION_ID) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.REQ_MSG_STR %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.REQ_MSG_STR) %>"/>
        <input type="hidden" name="<%= SAMLSSOProviderConstants.RELAY_STATE %>"
               value="<%= request.getAttribute(SAMLSSOProviderConstants.RELAY_STATE) %>"/>
        <button type="submit">POST</button>
    </p>
</form>

<script type="text/javascript">
    document.forms[0].submit();
</script>

</body>
</html>