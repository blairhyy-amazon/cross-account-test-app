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

val javaVersion = if (project.hasProperty("javaVersion")) {
  project.property("javaVersion").toString()
} else {
  "21"
}
val javaVersionRefactored = JavaVersion.toVersion(javaVersion)

plugins {
  java
  application
  id("org.springframework.boot") version "3.4.0"
  id("io.spring.dependency-management") version "1.1.0"
  id("com.google.cloud.tools.jib")
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

group = "com.amazon.sampleapp"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = javaVersionRefactored
java.targetCompatibility = javaVersionRefactored

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform("software.amazon.awssdk:bom:2.20.78"))
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-logging")
  implementation("io.opentelemetry:opentelemetry-api:1.34.1")
  implementation("software.amazon.awssdk:s3")
  implementation("software.amazon.awssdk:sts")
  implementation("com.mysql:mysql-connector-j:8.4.0")
  implementation ("org.apache.httpcomponents:httpclient:4.5.13")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")
  implementation("com.amazonaws:aws-java-sdk-sqs:1.12.400")
  implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.400")
  implementation("com.amazonaws:aws-java-sdk-kinesis:1.12.400")
  implementation("com.amazonaws:aws-java-sdk-lambda:1.12.400")
  implementation("com.amazonaws:aws-java-sdk-core:1.12.400")
  implementation("software.amazon.awssdk:sqs:2.20.2")
  implementation("software.amazon.awssdk:kinesis:2.20.1")
  implementation("software.amazon.awssdk:dynamodb:2.20.1")
  implementation("software.amazon.awssdk:sns:2.20.1")
  implementation("software.amazon.awssdk:sts:2.20.1")
  implementation("com.amazonaws:aws-java-sdk-sns:1.12.261")
  testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
  implementation("com.amazonaws:aws-java-sdk-sts:1.12.529")
}

jib {
  from {
    image = "openjdk:$javaVersion-jdk"
  }
  // Replace this value with the ECR Image URI
  to {
    image = "571600868874.dkr.ecr.us-west-1.amazonaws.com/adot_java_testing_app_v2:latest"
  }
  container {
    mainClass = "com.amazon.sampleapp.FrontendService"
    jvmFlags = listOf("-XX:+UseG1GC")
    ports = listOf("8080")
  }
}

application {
  mainClass.set("com.amazon.sampleapp.FrontendService")
}
