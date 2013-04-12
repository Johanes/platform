/*
 * Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.identity.openidconnect;

import java.util.Calendar;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.openidconnect.as.messages.IDTokenBuilder;
import org.apache.oltu.openidconnect.as.messages.IDTokenException;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AccessTokenRespDTO;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;

/**
 * This is the IDToken generator for the OpenID Connect Implementation. This
 * IDToken Generator utilizes the Amber IDTokenBuilder to build the IDToken.
 * 
 */
public class IDTokenGenerator {

	private static Log log = LogFactory.getLog(IDTokenGenerator.class);
	private static boolean DEBUG = log.isDebugEnabled();

	private OAuthTokenReqMessageContext request = null;
	private OAuth2AccessTokenRespDTO response = null;

	public IDTokenGenerator(OAuthTokenReqMessageContext tokReqMsgCtx,
	                        OAuth2AccessTokenRespDTO tokenRespDTO) {
		request = tokReqMsgCtx;
		response = tokenRespDTO;
	}

	public String generateToken() throws IdentityOAuth2Exception {
		OAuthServerConfiguration config = OAuthServerConfiguration.getInstance();
		String issuer = config.getOpenIDConnectIDTokenIssuer();
		int lifetime = Integer.parseInt(config.getOpenIDConnectIDTokenExpiration()) * 1000;
		int curTime = (int) Calendar.getInstance().getTimeInMillis();

		if (DEBUG) {
			log.debug("Using issuer " + issuer);
			log.debug("ID Token expiration seconds" + lifetime);
			log.debug("Current time " + curTime);
		}

		try {
			return new IDTokenBuilder().setIssuer(issuer)
			                           .setSubject(request.getAuthorizedUser())
			                           .setAudience(request.getOauth2AccessTokenReqDTO().getClientId())
			                           .setAuthorizedParty(request.getOauth2AccessTokenReqDTO().getClientId())
			                           .setExpiration(curTime + lifetime).setIssuedAt(curTime)
			                           .buildIDToken();
		} catch (IDTokenException e) {
			throw new IdentityOAuth2Exception("Erro while generating the IDToken", e);
		}

	}

}
