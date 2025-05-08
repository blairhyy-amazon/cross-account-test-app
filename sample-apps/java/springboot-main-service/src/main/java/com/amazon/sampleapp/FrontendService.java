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

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3.S3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.util.Collections;

@SpringBootApplication
public class FrontendService {

  @Bean
  public CloseableHttpClient httpClient() {
    return HttpClients.createDefault();
  }

  @Bean
  public S3Client s3() {
    return S3Client.builder().build();
  }

  @Bean
  public AmazonSQS amazonSQS() {
    // Automatically uses the IAM role associated with the ECS task
    return AmazonSQSClientBuilder.standard()
      .build();
  }

  @Bean
  public SqsClient sqsClient() {
    // Automatically uses the IAM role associated with the ECS task
    return SqsClient.builder().build();
  }

//  @Bean
//  public AmazonSNS amazonSNS() {
//    //  v1
//    return AmazonSNSClientBuilder.standard()
//            .build();
//  }

  @Bean
  public SnsClient snsClient() {
    // v2
    return SnsClient.builder().build();
  }

//  @Bean
//  public KinesisClient kinesisClient() {
//    // v2
//    return KinesisClient.builder().build();
//  }


  @Bean
  public AmazonKinesis kinesisClient() {
    // v1
    return AmazonKinesisClientBuilder.standard().build();
  }

  @Bean
  public DynamoDbClient DynamoDB() {
    return DynamoDbClient.builder().build();
  }


  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(FrontendService.class);
    app.setDefaultProperties(Collections.singletonMap("server.port", "8082"));
    app.run(args);
  }
}
