package com.myorg;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
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
import software.constructs.Construct;

public class Service01Stack extends Stack {
        public Service01Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic) {
                this(scope, id, null, cluster, productEventsTopic);
        }

        public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic) {
                super(scope, id, props);

                Map<String, String> environment = new HashMap<>();
                environment.put("SPRING_DATASOURCE_URL", "jdbc:mysql://"
                                + Fn.importValue("rds-endpoint")
                                + ":3306/aws-project01-db?createDatabaseIfNotExist=true&serverTimezone=UTC");
                environment.put("SPRING_DATASOURCE_USERNAME", "admin");
                environment.put("SPRING_DATASOURCE_PASSWORD", Fn.importValue("rds-password"));

                ApplicationLoadBalancedFargateService service01 = ApplicationLoadBalancedFargateService.Builder
                                .create(this, "ALB01")
                                .serviceName("service-01")
                                .cluster(cluster)
                                .cpu(256)
                                .memoryLimitMiB(512)
                                .desiredCount(2)
                                .listenerPort(8080)
                                .taskImageOptions(
                                                ApplicationLoadBalancedTaskImageOptions.builder()
                                                                .containerName("aws-project-01")
                                                                .image(ContainerImage.fromRegistry(
                                                                                "public.ecr.aws/l0t9d3q4/curso-aws-spring"))
                                                                .containerPort(8080)
                                                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                                                                .logGroup(LogGroup.Builder.create(this,
                                                                                                "Service01LogGroup")
                                                                                                .logGroupName("service-01")
                                                                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                                                                .build())
                                                                                .streamPrefix("service-01")
                                                                                .build()))
                                                                .environment(environment)
                                                                .build())
                                .publicLoadBalancer(true)
                                .healthCheckGracePeriod(Duration.seconds(90))
                                .assignPublicIp(true)
                                .build();

                service01.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                                .path("/actuator/health")
                                .port("8080")
                                .healthyHttpCodes("200")
                                .healthyThresholdCount(3)
                                .build());

                ScalableTaskCount scalableTaskCount = service01
                                .getService()
                                .autoScaleTaskCount(EnableScalingProps.builder()
                                                .minCapacity(2)
                                                .maxCapacity(4)
                                                .build());

                scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling", CpuUtilizationScalingProps.builder()
                                .targetUtilizationPercent(70)
                                .build());

                productEventsTopic.getTopic().grantPublish(service01.getTaskDefinition().getTaskRole());
        }
}
