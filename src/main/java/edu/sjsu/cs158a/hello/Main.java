package edu.sjsu.cs158a.hello;

import com.google.protobuf.ExtensionLite;
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
    void register(@Parameters(paramLabel = "hostPort") String hostPort,
                  @Parameters(paramLabel = "className") String courseName,
                  @Parameters(paramLabel = "studentID") int SSID,
                  @Parameters(paramLabel = "studentName") String name)
    {
        /* Request a code using requestCode(String course, int studentID)
         * Code Response -
         *    RC: 0 = add code received, 1 = Invalid course, 2 = Invalid ID
         *    Add Code: int
         *
         * Use register(int addCode, int studentID, String name)
         * Registration response
         *    RC: 0 = Success, 1 = Invalid Code, 2 = Code does not match ID
         */

        try
        {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
            var stub = HelloGrpc.newBlockingStub(channel);
            var response = stub.requestCode(Messages.CodeRequest.newBuilder().setCourse(courseName)
                    .setSsid(SSID).build());

        }
        catch (StatusRuntimeException e)
        {
            e.printStackTrace();
            System.out.println("problem communicating with " + hostPort);
        }
    }

    @Command
    void server(@Parameters(paramLabel = "port") int port) throws InterruptedException {
        class AddExampleImpl extends AddExampleGrpc.AddExampleImplBase {
            int total = 0;
            @Override
            public void add(Messages.AddExampleRequest request,
                            StreamObserver<Messages.AddExampleResponse> responseObserver) {
                var a = request.getA();
                var b = request.getB();
                var sum = a + b;
                int myTotal;
                synchronized (this) {
                    total += sum;
                    myTotal = total;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                var response = Messages.AddExampleResponse.newBuilder()
                        .setResult(MessageFormat.format("{0} + {1} = {2} total {3}", a, b, sum, myTotal))
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
    }
    public static void main(String[] args)
    {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}