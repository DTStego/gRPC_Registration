package edu.sjsu.cs158a.hello;

import edu.sjsu.cs158a.hello.HelloGrpc.HelloImplBase;
import edu.sjsu.cs158a.hello.Messages.CodeRequest;
import edu.sjsu.cs158a.hello.Messages.CodeResponse;
import edu.sjsu.cs158a.hello.Messages.ListRequest;
import edu.sjsu.cs158a.hello.Messages.ListResponse;
import edu.sjsu.cs158a.hello.Messages.RegisterRequest;
import edu.sjsu.cs158a.hello.Messages.RegisterResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Command
public class Main {
    @Command
    int add(@Parameters(paramLabel = "hostPort") String hostPort,
            @Parameters(paramLabel = "a") int a,
            @Parameters(paramLabel = "b") int b) {
        try {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();

            // Represents a remote object.
            var stub = AddExampleGrpc.newBlockingStub(channel);

            // Actual agreed upon object.
            var request = Messages.AddExampleRequest.newBuilder().setA(a).setB(b).build();

            // Add the agreed information to the stub.
            var rsp = stub.add(request);
            System.out.println(rsp.getResult());
        } catch (StatusRuntimeException e) {
            System.out.println("problem communicating with " + hostPort);
        }
        return 0;
    }

    @Command
    int server(@Parameters(paramLabel = "port") int port) throws InterruptedException {
        class AddExampleImpl extends AddExampleGrpc.AddExampleImplBase {

            int total = 0;
            @Override
            public void add(Messages.AddExampleRequest request,
                            StreamObserver<Messages.AddExampleResponse> responseObserver) {
                var a = request.getA();
                var b = request.getB();
                var sum = a + b;
                
                synchronized (this)
                {
                    total += sum;
                }
                var response = Messages.AddExampleResponse.newBuilder()
                        .setResult(MessageFormat.format("{0} + {1} = {2} total {3}", a, b, sum, total))
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }
        try {
            var server = ServerBuilder.forPort(port).addService(new AddExampleImpl()).build();
            server.start();
            server.awaitTermination();
        } catch (IOException e) {
            System.out.println("couldn't serve on " + port);
        }
        return 0;
    }
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}