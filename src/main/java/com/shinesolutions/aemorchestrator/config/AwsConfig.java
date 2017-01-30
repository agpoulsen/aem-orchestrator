package com.shinesolutions.aemorchestrator.config;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.shinesolutions.aemorchestrator.model.AutoScaleGroupNames;
import com.shinesolutions.aemorchestrator.service.AwsHelperService;

@Configuration
@Profile("default")
public class AwsConfig {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${aws.sqs.queueName}")
    private String queueName;

    @Value("${aws.region}")
    private String regionString;

    @Value("${aws.client.useProxy}")
    private Boolean useProxy;

    @Value("${aws.client.protocol}")
    private String clientProtocol;

    @Value("${aws.client.proxy.host}")
    private String clientProxyHost;

    @Value("${aws.client.proxy.port}")
    private Integer clientProxyPort;

    @Value("${aws.client.connection.timeout}")
    private Integer clientConnectionTimeout;

    @Value("${aws.client.max.errorRetry}")
    private Integer clientMaxErrorRetry;
    
    @Value("${aws.cloudformation.stackName.publishDispatcher}")
    private String awsPublisherDispatcherStackName;
    
    @Value("${aws.cloudformation.stackName.publish}")
    private String awsPublisherStackName;
    
    @Value("${aws.cloudformation.stackName.authorDispatcher}")
    private String awsAuthorDispatcherStackName;

    @Value("${aws.cloudformation.autoScaleGroup.logicalId.publishDispatcher}")
    private String awsPublisherDispatcherLogicalId;

    @Value("${aws.cloudformation.autoScaleGroup.logicalId.publish}")
    private String awsPublisherLogicalId;

    @Value("${aws.cloudformation.autoScaleGroup.logicalId.authorDispatcher}")
    private String awsAuthorDispatcherLogicalId;


    @Bean
    public AWSCredentialsProvider awsCredentialsProvider() {
        /*
         * For info on how this works, see:
         * http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/
         * credentials.html
         */
        return new DefaultAWSCredentialsProviderChain();
    }

    @Bean
    public Region awsRegion() {
        Region region = RegionUtils.getRegion(regionString);
        if (region == null) {
            throw new InvalidPropertyException(Region.class, "aws.region", "Unknown AWS region: " + regionString);
        }
        return region;
    }

    @Bean
    public SQSConnection sqsConnection(AWSCredentialsProvider awsCredentialsProvider, Region awsRegion,
        ClientConfiguration awsClientConfig) throws JMSException {

        SQSConnectionFactory connectionFactory = SQSConnectionFactory.builder().withRegion(awsRegion)
            .withAWSCredentialsProvider(awsCredentialsProvider).withClientConfiguration(awsClientConfig).build();

        // Create the connection
        SQSConnection connection = connectionFactory.createConnection();

        return connection;
    }

    @Bean
    public MessageConsumer sqsMessageConsumer(SQSConnection connection) throws JMSException {

        /*
         * Create the session and use CLIENT_ACKNOWLEDGE mode. Acknowledging
         * messages deletes them from the queue
         */
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));

        return consumer;
    }

    @Bean
    public ClientConfiguration awsClientConfig() {
        ClientConfiguration clientConfig = new ClientConfiguration();

        if (useProxy) {
            clientConfig.setProxyHost(clientProxyHost);
            clientConfig.setProxyPort(clientProxyPort);
        }

        clientConfig.setProtocol(Protocol.valueOf(clientProtocol.toUpperCase()));
        clientConfig.setConnectionTimeout(clientConnectionTimeout);
        clientConfig.setMaxErrorRetry(clientMaxErrorRetry);

        return clientConfig;
    }

    @Bean
    public AmazonEC2 amazonEC2Client(AWSCredentialsProvider awsCredentialsProvider,
        ClientConfiguration awsClientConfig) {
        return AmazonEC2ClientBuilder.standard()
            .withCredentials(awsCredentialsProvider)
            .withClientConfiguration(awsClientConfig)
            .build();
    }

    @Bean
    public AmazonElasticLoadBalancing amazonElbClient(AWSCredentialsProvider awsCredentialsProvider,
        ClientConfiguration awsClientConfig) {
        return AmazonElasticLoadBalancingClientBuilder.standard()
            .withCredentials(awsCredentialsProvider)
            .withClientConfiguration(awsClientConfig)
            .build();
    }

    @Bean
    public AmazonAutoScaling amazonAutoScalingClient(AWSCredentialsProvider awsCredentialsProvider,
        ClientConfiguration awsClientConfig) {
        return AmazonAutoScalingClientBuilder.standard()
            .withCredentials(awsCredentialsProvider)
            .withClientConfiguration(awsClientConfig)
            .build();
    }

    @Bean
    public AmazonCloudFormation amazonCloudFormationClient(AWSCredentialsProvider awsCredentialsProvider,
        ClientConfiguration awsClientConfig) {
        return AmazonCloudFormationClientBuilder.standard()
            .withCredentials(awsCredentialsProvider)
            .withClientConfiguration(awsClientConfig)
            .build();
    }

    @Bean
    public AutoScaleGroupNames autoScaleGroupNames(final AwsHelperService awsHelper) {
        AutoScaleGroupNames asgNames = new AutoScaleGroupNames();

        asgNames.setPublishDispatcher(
            awsHelper.getStackPhysicalResourceId(awsPublisherDispatcherStackName, awsPublisherDispatcherLogicalId));
        logger.info("Resolved auto scaling group name for publisher dispatcher to: " + asgNames.getPublishDispatcher());

        asgNames.setPublish(
            awsHelper.getStackPhysicalResourceId(awsPublisherStackName, awsPublisherLogicalId));
        
        logger.info("Resolved auto scaling group name for publisher to: " + asgNames.getPublish());

        asgNames.setAuthorDispatcher(
            awsHelper.getStackPhysicalResourceId(awsAuthorDispatcherStackName, awsAuthorDispatcherLogicalId));
        
        logger.info("Resolved auto scaling group name for author dispatcher to: " + asgNames.getAuthorDispatcher());

        return asgNames;
    }

}
