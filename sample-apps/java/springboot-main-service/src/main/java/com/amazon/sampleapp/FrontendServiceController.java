/*
 * Copyright Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.sampleapp;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import software.amazon.awssdk.services.sqs.SqsClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.regions.Regions;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
//import com.amazonaws.services.sns.AmazonSNSClientBuilder;
//import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
//import com.amazonaws.services.sns.model.PublishResult;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
//import software.amazon.awssdk.services.kinesis.KinesisClient;
//import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
//import software.amazon.awssdk.services.kinesis.model.CreateStreamResponse;
//import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
//import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
//import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
//import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
//import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
//import software.amazon.awssdk.services.sts.StsClient;
//import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
//import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
//import software.amazon.awssdk.services.sts.model.Credentials;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
//import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Controller
public class FrontendServiceController {
  private static final Logger logger = LoggerFactory.getLogger(FrontendServiceController.class);
  private final CloseableHttpClient httpClient;
  private final S3Client s3;
  private final AmazonSQS amazonSQS;
//  private final AmazonSNS amazonSNS;
  private final SnsClient snsClient;
  private final SqsClient sqsClient;
  private final AmazonKinesis kinesisClient;
//  private final KinesisClient kinesisClient;
//  private final DynamoDbClient dynamoDbClient;
//  private final AmazonDynamoDB dynamoDbClient;

  private AtomicBoolean shouldSendLocalRootClientCall = new AtomicBoolean(false);

  @Bean
  private ScheduledExecutorService runLocalRootClientCallRecurringService() { // run the service
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    Runnable runnableTask =
            () -> {
              if (shouldSendLocalRootClientCall.get()) {
                shouldSendLocalRootClientCall.set(false);
                HttpGet request = new HttpGet("http://local-root-client-call");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                  HttpEntity entity = response.getEntity();
                  if (entity != null) {
                    logger.info(EntityUtils.toString(entity));
                  }
                } catch (Exception e) {
                  logger.error("Error in recurring task: {}", e.getMessage());
                }
              }
            };
    // Run with initial 0.1s delay, every 1 second
    executorService.scheduleAtFixedRate(runnableTask, 100, 1000, TimeUnit.MILLISECONDS);
    return executorService;
  }

  @Autowired
  public FrontendServiceController(CloseableHttpClient httpClient, S3Client s3, AmazonSQS amazonSQS, SqsClient sqsClient, SnsClient snsClient, AmazonKinesis kinesisClient) {
    this.httpClient = httpClient;
    this.s3 = s3;
    this.amazonSQS = amazonSQS;
    this.sqsClient = sqsClient;
    this.snsClient = snsClient;
    this.kinesisClient = kinesisClient;
//    this.dynamoDbClient = dynamoDbClient;
  }

  @GetMapping("/")
  @ResponseBody
  public String healthcheck() {
    return "healthcheck";
  }

  // Add new endpoint for SNS publishing
//  @PostMapping("/publish-sns")
//  @ResponseBody
//  public String publishToSNS(@RequestParam String message, @RequestParam String topicArn) {
//    try {
//      // v1
//
//      // Build STS client in specified region
//      AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
//              .withRegion("eu-central-1")
//              .build();
//
//      // Load the role ARN from an environment variable for security
//      String roleArn = System.getenv("ASSUME_ROLE_ARN");
//      String roleSessionName = "SNSPublishSession";
//
//      // Create a request to assume the specified role
//      AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
//              .withRoleArn(roleArn)
//              .withRoleSessionName(roleSessionName)
//              .withDurationSeconds(3600); // 1 hour session
//
//      // Assume the role and retrieve temporary session credentials
//      AssumeRoleResult assumeRoleResult = stsClient.assumeRole(assumeRoleRequest);
//      Credentials tempCredentials = assumeRoleResult.getCredentials();
//
//      // Wrap credentials in BasicSessionCredentials
//      BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
//              tempCredentials.getAccessKeyId(),
//              tempCredentials.getSecretAccessKey(),
//              tempCredentials.getSessionToken()
//      );
//
//      // Create SNS client using the temporary credentials from AssumeRole
//      AmazonSNS snsClient = AmazonSNSClientBuilder.standard()
//              .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
//              .withRegion("eu-central-1")
//              .build();
//
//      // Prepare and publish the message using the SNS client
//      PublishRequest publishRequest = new PublishRequest()
//              .withTopicArn(topicArn)
//              .withMessage(message);
//
//      PublishResult result = snsClient.publish(publishRequest);
//
//      logger.info("Message published to SNS. Topic ARN: " + topicArn);
//      logger.info("Message ID: " + result.getMessageId());
//
//      return getXrayTraceId();
//    } catch (Exception e) {
//      logger.error("Error publishing to SNS: {}", e.getMessage());
//      throw new RuntimeException(e);
//    }
//  }

//  @PostMapping("/publish-sns-v2")
//  @ResponseBody
//  public String publishToSNSv2(@RequestParam String message, @RequestParam String topicArn) {
//    try {
//      Region region = Region.EU_CENTRAL_1;
//
//      // Build STS client
//      StsClient stsClient = StsClient.builder()
//              .region(region)
//              .build();
//
//      String roleArn = System.getenv("ASSUME_ROLE_ARN");
//      String roleSessionName = "SNSPublishSession";
//
//      AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
//              .roleArn(roleArn)
//              .roleSessionName(roleSessionName)
//              .durationSeconds(3600)
//              .build();
//
//      AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);
//      Credentials tempCredentials = assumeRoleResponse.credentials();
//
//      AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
//              tempCredentials.accessKeyId(),
//              tempCredentials.secretAccessKey(),
//              tempCredentials.sessionToken()
//      );
//
//      SnsClient snsClient = SnsClient.builder()
//              .credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials))
//              .region(region)
//              .build();
//
//      PublishRequest publishRequest = PublishRequest.builder()
//              .topicArn(topicArn)
//              .message(message)
//              .build();
//
//      PublishResponse result = snsClient.publish(publishRequest);
//
//      logger.info("Message published to SNS. Topic ARN: " + topicArn);
//
//      return getXrayTraceId();
//    } catch (Exception e) {
//      logger.error("Error publishing to SNS: {}", e.getMessage());
//      throw new RuntimeException(e);
//    }
//  }

  @GetMapping("/get-sqs")
  @ResponseBody
  public String getMessage(){
    Context currentContext = Context.current();
    Span currentSpan = Span.fromContext(currentContext);

    currentSpan.addEvent("metric",Attributes.of(
                    AttributeKey.stringKey("metric.name"), "RequestSize",
                    AttributeKey.stringKey("metric.unit"), "Bytes",
                    AttributeKey.stringKey("metric.value"), String.valueOf(100)
                ));

    try {
      ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
        .queueUrl(System.getenv("SQS_QUEUE_URL"))
        .maxNumberOfMessages(1)  // Number of messages to retrieve (max: 10)
        .waitTimeSeconds(10)  // Long polling (wait up to 20 seconds for messages)
        .build();

      ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(receiveMessageRequest);
      logger.info("Sqs message received!");
    } catch (Exception e) {
      logger.error("Sqs message receiver failed!");
    }
    return getXrayTraceId();
  }

  @PostMapping("/send-sqs")
  @ResponseBody
  public String sendMessage(@RequestParam String message) {
    try {
      // Create a SendMessageRequest to send the message to the queue
      SendMessageRequest sendMessageRequest = new SendMessageRequest()
        .withQueueUrl(System.getenv("SQS_QUEUE_URL"))
        .withMessageBody(message);

      // Send the message to the SQS queue
      SendMessageResult result = amazonSQS.sendMessage(sendMessageRequest);
      if (result != null) {
        logger.info("Sqs message sent!");
      }
    } catch (Exception e) {
      logger.error("Sqs message failed!");
    }
    return getXrayTraceId();
  }

//  @PostMapping("/create-stream")
//  @ResponseBody
//  public String createStream(String streamName, int shardCount) {
//    try {
//      CreateStreamRequest request = CreateStreamRequest.builder()
//              .streamName(streamName)
//              .shardCount(shardCount)
//              .build();
//
//      CreateStreamResponse response = kinesisClient.createStream(request);
//      if (response != null) {
//        logger.info("Create stream done!");
//      }
//    } catch (Exception e) {
//      logger.error("Create stream failed!");
//    }
//    return getXrayTraceId();
//  }

  @GetMapping("/get-stream-v1")
  @ResponseBody
  public String getStreamArnv1(String streamName, String streamArn) {
    DescribeStreamRequest request = new DescribeStreamRequest()
                    .withStreamName(streamName)
                    .withStreamARN(streamArn);

    DescribeStreamResult response = kinesisClient.describeStream(request);

    return response.getStreamDescription().getStreamARN();
  }

//  @GetMapping("/get-stream-v2")
//  @ResponseBody
//  public String getStreamArnv2(String streamName, String streamArn) {
//    DescribeStreamRequest request = DescribeStreamRequest.builder()
//            .streamName(streamName)
//            .streamARN(streamArn)
//            .build();
//
//    DescribeStreamResponse response = kinesisClient.describeStream(request);
//
//    return response.streamDescription().streamARN();
//  }

  @GetMapping("/get-table-arn-v1")
  @ResponseBody
  public String getTableArnv1(String tableName) {
    String roleArn = System.getenv("ASSUME_ROLE_ARN");
    String roleSessionName = "SNSPublishSession";

    // Create STS client
    AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
            .withRegion("us-east-2")
            .build();

    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
            .withRoleArn(roleArn)
            .withRoleSessionName(roleSessionName)
            .withDurationSeconds(3600);

    AssumeRoleResult assumeRoleResult = stsClient.assumeRole(assumeRoleRequest);
    Credentials tempCredentials = assumeRoleResult.getCredentials();

    BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
            tempCredentials.getAccessKeyId(),
            tempCredentials.getSecretAccessKey(),
            tempCredentials.getSessionToken()
    );

    AmazonDynamoDB dynamoDbClient = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
            .withRegion("us-east-2")
            .build();

    DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
    DescribeTableResult result = dynamoDbClient.describeTable(request);
    TableDescription tableDescription = result.getTable();
    String tableArn = tableDescription.getTableArn();
    logger.info("Successfully retrieved ARN for table: {} using assumed role", tableArn);

    return getXrayTraceId();
  }

//  @GetMapping("/get-table-arn-v2")
//  @ResponseBody
//  public String getTableArnv2(String tableName) {
//    Region region = Region.US_EAST_2;
//
//    // Create STS client
//    StsClient stsClient = StsClient.builder()
//            .region(region)
//            .build();
//
//    String roleArn = System.getenv("ASSUME_ROLE_ARN");
//    String roleSessionName = "SNSPublishSession";
//
//    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
//            .roleArn(roleArn)
//            .roleSessionName(roleSessionName)
//            .durationSeconds(3600)
//            .build();
//    AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);
//    Credentials tempCredentials = assumeRoleResponse.credentials();
//
//    AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
//            tempCredentials.accessKeyId(),
//            tempCredentials.secretAccessKey(),
//            tempCredentials.sessionToken()
//    );
//
//    DynamoDbClient crossAccountDynamoDbClient = DynamoDbClient.builder()
//            .region(region)
//            .credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials))
//            .build();
//
//    // Make the DynamoDB call with assumed role
//    DescribeTableRequest request = DescribeTableRequest.builder()
//            .tableName(tableName)
//            .build();
//
//    DescribeTableResponse response = crossAccountDynamoDbClient.describeTable(request);
//    TableDescription tableDescription = response.table();
//
//    if (tableDescription != null) {
//      logger.info("Successfully retrieved ARN for table: {} using assumed role", tableName);
//      return response.table().tableArn();
//      // Use the table ARN as needed
//    } else {
//      logger.error("Failed to retrieve ARN for table: {}", tableName);
//      return "Failed to retrieve ARN for table: " + tableName;
//    }
//  }

  // test aws calls instrumentation
  @GetMapping("/aws-sdk-call")
  @ResponseBody
  public String awssdkCall(@RequestParam(name = "testingId", required = false) String testingId) {
    String bucketName = "e2e-test-bucket-name";
    if (testingId != null) {
      bucketName += "-" + testingId;
    }
    GetBucketLocationRequest bucketLocationRequest =
            GetBucketLocationRequest.builder().bucket(bucketName).build();
    try {
      s3.getBucketLocation(bucketLocationRequest);
    } catch (Exception e) {
      logger.error("Error occurred when trying to get bucket location of: " + bucketName, e);
    }
    return getXrayTraceId();
  }

  // test http instrumentation (Apache HttpClient for Java 8)
  @GetMapping("/outgoing-http-call")
  @ResponseBody
  public String httpCall() {
    HttpGet request = new HttpGet("https://www.amazon.com");
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      logger.info("outgoing-http-call status code: " + statusCode);
    } catch (Exception e) {
      logger.error("Could not complete HTTP request: {}", e.getMessage());
    }
    return getXrayTraceId();
  }

  // RemoteService must also be deployed to use this API
  @GetMapping("/remote-service")
  @ResponseBody
  public String downstreamService(@RequestParam("ip") String ip) {
    ip = ip.replace("/", "");
    HttpGet request = new HttpGet("http://" + ip + ":8083/healthcheck");
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      logger.info("Remote service call status code: " + statusCode);
      return getXrayTraceId();
    } catch (Exception e) {
      logger.error("Could not complete HTTP request to remote service: {}", e.getMessage());
    }
    return getXrayTraceId();
  }

  // Test Local Root Client Span generation
  @GetMapping("/client-call")
  @ResponseBody
  public String asyncService() {
    logger.info("Client-call received");
    shouldSendLocalRootClientCall.set(true);
    return "{\"traceId\": \"1-00000000-000000000000000000000000\"}";
  }

  // Uses the /mysql endpoint to make an SQL call
  @GetMapping("/mysql")
  @ResponseBody
  public String mysql() {
    logger.info("mysql received");
    final String rdsMySQLClusterPassword = new String(new Base64().decode(System.getenv("RDS_MYSQL_CLUSTER_PASSWORD").getBytes()));
    try {
      Connection connection = DriverManager.getConnection(
              System.getenv("RDS_MYSQL_CLUSTER_CONNECTION_URL"),
              System.getenv("RDS_MYSQL_CLUSTER_USERNAME"),
              rdsMySQLClusterPassword);
      Statement statement = connection.createStatement();
      statement.executeQuery("SELECT * FROM `tables` LIMIT 1;");
    } catch (SQLException e) {
      logger.error("Could not complete SQL request: {}", e.getMessage());
      throw new RuntimeException(e);
    }
    return getXrayTraceId();
  }

  @GetMapping("/get-lambda-arn-v1")
  @ResponseBody
  public static String getLambdaArn(String functionName, String region) {
    // 1. STS client in the caller's account
    AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
            .withRegion("us-east-2")
            .build();

    String roleArn = System.getenv("ASSUME_ROLE_ARN");
    String roleSessionName = "LambdaSession";

    // Create a request to assume the specified role
    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
            .withRoleArn(roleArn)
            .withRoleSessionName(roleSessionName)
            .withDurationSeconds(3600); // 1 hour session

    // Assume the role and retrieve temporary session credentials
    AssumeRoleResult assumeRoleResult = stsClient.assumeRole(assumeRoleRequest);
    Credentials tempCredentials = assumeRoleResult.getCredentials();

    // Wrap credentials in BasicSessionCredentials
    BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
            tempCredentials.getAccessKeyId(),
            tempCredentials.getSecretAccessKey(),
            tempCredentials.getSessionToken()
    );

    AWSLambda lambdaClient = AWSLambdaClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
            .withRegion("us-east-2")
            .build();

    // 4. Fetch the Lambda function ARN
    GetFunctionRequest request = new GetFunctionRequest().withFunctionName(functionName);
    GetFunctionResult result = lambdaClient.getFunction(request);
    return result.getConfiguration().getFunctionArn();
  }


  // get x-ray trace id
  private String getXrayTraceId() {
    String traceId = Span.current().getSpanContext().getTraceId();
    String xrayTraceId = "1-" + traceId.substring(0, 8) + "-" + traceId.substring(8);
    return String.format("{\"traceId\": \"%s\"}", xrayTraceId);
  }
}
