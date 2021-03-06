package org.wso2.carbon.connectors.twilio.queue;

import java.util.HashMap;
import java.util.Map;

import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.wso2.carbon.connector.twilio.AbstractTwilioConnector;
import org.wso2.carbon.mediation.library.connectors.core.ConnectException;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.instance.Queue;
/*
 * Class mediator for updating a queue instance
 * For more information, see http://www.twilio.com/docs/api/rest/queue
 */
public class UpdateQueue extends AbstractTwilioConnector {

	public void connect(MessageContext messageContext) throws ConnectException {

		SynapseLog log = getLog(messageContext);
		String accountSid = (String) messageContext.getProperty("TwilioAccountSid");
		String authToken = (String) messageContext.getProperty("TwilioAuthToken");

		String queueSid = (String) messageContext.getProperty("TwilioQueueSid");

		String friendlyName = (String) messageContext
				.getProperty("TwilioQueueFriendlyName");
		String maxSize = (String) messageContext.getProperty("TwilioQueueMaxSize");

		Map<String, String> params = new HashMap<String, String>();

		if (friendlyName != null) {
			params.put("FriendlyName", friendlyName);
		}

		if (maxSize != null) {
			params.put("MaxSize", maxSize);
		}

		try {
			updateQueue(accountSid, authToken, queueSid, log, params);
		} catch (Exception e) {
			log.auditError(e.getMessage());
			throw new SynapseException(e);
		}

	}

	private void updateQueue(String accountSid, String authToken, String queueSid,
			SynapseLog log, Map<String, String> params) throws TwilioRestException,
			IllegalArgumentException {

		TwilioRestClient twilioRestClient = new TwilioRestClient(accountSid, authToken);

		Queue queue = twilioRestClient.getAccount().getQueue(queueSid);
		queue.update(params);

		// TODO: change response
		log.auditLog("Queue editing successful.");
	}

}
