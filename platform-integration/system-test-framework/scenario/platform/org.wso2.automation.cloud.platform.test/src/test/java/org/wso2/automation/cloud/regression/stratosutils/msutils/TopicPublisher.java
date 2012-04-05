package org.wso2.automation.cloud.regression.stratosutils.msutils;

import org.wso2.platform.test.core.ProductConstant;
import org.wso2.platform.test.core.utils.UserInfo;
import org.wso2.platform.test.core.utils.frameworkutils.FrameworkFactory;

import javax.jms.JMSException;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

public class TopicPublisher {

    public static final String QPID_ICF = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";
    protected static final String CF_NAME_PREFIX = "connectionfactory.";
    protected static final String CF_NAME = "qpidConnectionfactory";
    protected static String userName;
    protected static String password;

    private static String CARBON_CLIENT_ID = "carbon";
    private static String CARBON_VIRTUAL_HOST_NAME = "carbon";
    private static String CARBON_DEFAULT_HOSTNAME = FrameworkFactory.getFrameworkProperties(ProductConstant.MS_SERVER_NAME).getProductVariables().getHostName();
    private static String CARBON_DEFAULT_PORT = FrameworkFactory.getFrameworkProperties(ProductConstant.MS_SERVER_NAME).getProductVariables().getQpidPort();
    private static String queueName = "testQueueQA2";
    private String topicName = "MYTopic";

    public TopicPublisher(UserInfo userInfo){
        this.userName = userInfo.getUserName().replaceAll("@", "!");
        this.password = userInfo.getPassword();
    }

    public void publishMessage() throws NamingException, JMSException {
        Properties properties = new Properties();
        properties.put(Context.INITIAL_CONTEXT_FACTORY, QPID_ICF);
        properties.put(CF_NAME_PREFIX + CF_NAME, getTCPConnectionURL(userName, password));

        System.out.println("getTCPConnectionURL(userName,password) = " + getTCPConnectionURL(userName, password));

        InitialContext ctx = new InitialContext(properties);
        // Lookup connection factory
        TopicConnectionFactory connFactory = (TopicConnectionFactory) ctx.lookup(CF_NAME);
        TopicConnection topicConnection = connFactory.createTopicConnection();
        topicConnection.start();
        TopicSession topicSession =
                topicConnection.createTopicSession(false, QueueSession.AUTO_ACKNOWLEDGE);
        // Send message
        Topic topic = topicSession.createTopic(topicName);
        // create the message to send
        TextMessage textMessage = topicSession.createTextMessage("TEST Message");

        javax.jms.TopicPublisher topicPublisher = topicSession.createPublisher(topic);
        topicPublisher.publish(textMessage);
        topicSession.close();
        topicConnection.close();
    }

    public String getTCPConnectionURL(String username, String password) {
        // amqp://{username}:{password}@carbon/carbon?brokerlist='tcp://{hostname}:{port}'
        return new StringBuffer()
                .append("amqp://").append(username).append(":").append(password)
                .append("@").append(CARBON_CLIENT_ID)
                .append("/").append(CARBON_VIRTUAL_HOST_NAME)
                .append("?brokerlist='tcp://").append(CARBON_DEFAULT_HOSTNAME).append(":").append(CARBON_DEFAULT_PORT).append("'")
                .toString();
    }
}


