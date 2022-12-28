package com.myorg;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

public class Service02Stack extends Stack {
        public Service02Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic) {
                this(scope, id, null, cluster, productEventsTopic);
        }

        public Service02Stack(final Construct scope, final String id, final StackProps props, Cluster cluster,
                        SnsTopic productEventsTopic) {
                super(scope, id, props);

                Queue productEventsDlq = Queue.Builder
                                .create(this, "ProductEventsDLQ")
                                .queueName("product-events-dlq")
                                .build();

                DeadLetterQueue deadLetterQueue = DeadLetterQueue
                                .builder()
                                .queue(productEventsDlq)
                                .maxReceiveCount(3)
                                .build();

                Queue productEventsQueue = Queue.Builder
                                .create(this, "ProductEvents")
                                .queueName("product-events")
                                .deadLetterQueue(deadLetterQueue)
                                .build();

                SqsSubscription sqsSubscription = SqsSubscription.Builder
                                .create(productEventsQueue)
                                .build();

                productEventsTopic.getTopic().addSubscription(sqsSubscription);

                Map<String, String> environment = new HashMap<>();
                environment.put("AWS_REGION", "us-east-1");
                environment.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

                ApplicationLoadBalancedFargateService service02 = ApplicationLoadBalancedFargateService.Builder
                                .create(this, "ALB02")
                                .serviceName("service-02")
                                .cluster(cluster)
                                .cpu(256)
                                .memoryLimitMiB(512)
                                .desiredCount(2)
                                .listenerPort(9090)
                                .taskImageOptions(
                                                ApplicationLoadBalancedTaskImageOptions.builder()
                                                                .containerName("aws-project-02")
                                                                .image(ContainerImage.fromRegistry(
                                                                                "public.ecr.aws/l0t9d3q4/curso-aws-spring-app02"))
                                                                .containerPort(9090)
                                                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                                                                .logGroup(LogGroup.Builder.create(this,
                                                                                                "Service02LogGroup")
                                                                                                .logGroupName("service-02")
                                                                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                                                                .build())
                                                                                .streamPrefix("service-02")
                                                                                .build()))
                                                                .environment(environment)
                                                                .build())
                                .publicLoadBalancer(true)
                                .healthCheckGracePeriod(Duration.seconds(180))
                                .assignPublicIp(true)
                                .build();

                service02.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                                .path("/actuator/health")
                                .port("9090")
                                .healthyHttpCodes("200")
                                .healthyThresholdCount(3)
                                .build());

                ScalableTaskCount scalableTaskCount = service02
                                .getService()
                                .autoScaleTaskCount(EnableScalingProps.builder()
                                                .minCapacity(2)
                                                .maxCapacity(4)
                                                .build());

                scalableTaskCount.scaleOnCpuUtilization("Service02AutoScaling", CpuUtilizationScalingProps.builder()
                                .targetUtilizationPercent(70)
                                .build());

                productEventsQueue.grantConsumeMessages(service02.getTaskDefinition().getTaskRole());

        }
}
