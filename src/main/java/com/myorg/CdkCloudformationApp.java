package com.myorg;

import software.amazon.awscdk.App;

public class CdkCloudformationApp {

    public static void main(final String[] args) {
        App app = new App();

        VpcStack vpcStack = new VpcStack(app, "Vpc");

        RdsStack rdsStack = new RdsStack(app, "Rds", vpcStack.getVpc());
        rdsStack.addDependency(vpcStack);

        ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc());
        clusterStack.addDependency(vpcStack);

        SnsStack snsStack = new SnsStack(app, "Sns");

        Service01Stack service01Stack = new Service01Stack(app, "Service01", clusterStack.getCluster(), snsStack.getProductEventsTopic());
        service01Stack.addDependency(clusterStack);
        service01Stack.addDependency(rdsStack);
        service01Stack.addDependency(snsStack);

        app.synth();
    }
}
