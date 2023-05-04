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
import java.util.*;

@Command
public class Main
{
    @Command
    void register(@Parameters(paramLabel = "hostPort") String hostPort,
                  @Parameters(paramLabel = "className") String courseName,
                  @Parameters(paramLabel = "studentID") int SSID,
                  @Parameters(paramLabel = "studentName") String name)
    {
        /* Request a code using requestCode(String course, int studentID)
         * Code Response -
         *    int RC: 0 = add code received, 1 = Invalid course, 2 = Invalid ID
         *    int addCode
         *
         * Use register(int addCode, int studentID, String name)
         * Registration response -
         *    int RC: 0 = Success, 1 = Invalid Code, 2 = Code does not match ID
         */
        try
        {
            // Requesting AddCode Section
            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
            var stub = HelloGrpc.newBlockingStub(channel);

            var codeResponse = stub.requestCode(Messages.CodeRequest.newBuilder().setCourse(courseName)
                    .setSsid(SSID).build());

            // Get the rc from CodeResponse.
            int addCodeStatus = codeResponse.getRc();

            // Only proceed if the RC is 0, otherwise, print an error message.
            if (addCodeStatus != 0)
            {
                System.out.println("problem getting add code: " + addCodeStatus);
                return;
            }

            int addCode = codeResponse.getAddcode();

            // Registration Section
            var registrationResponse = stub.register(Messages.RegisterRequest.newBuilder()
                    .setAddCode(addCode)
                    .setSsid(SSID)
                    .setName(name).build());

            int registrationResponseStatus = registrationResponse.getRc();

            if (registrationResponseStatus == 0)
            {
                System.out.println("registration successful");
            }
            else
            {
                System.out.println("problem registering: " + registrationResponseStatus);
            }

            // OPTIONAL
            // channel.shutdown();
        }
        catch (StatusRuntimeException e)
        {
            System.out.println("problem communicating with " + hostPort);
        }
    }

    @Command
    void listStudents(@Parameters(paramLabel = "hostPort") String hostPort,
                      @Parameters(paramLabel = "className") String courseName)
    {
        /*
         * List the students in a specific class (CS158A/CS158B).
         * Sort students in ascending order based on ID.
         *
         * Use list(String className)
         * ListResponse -
         *     int RC: 0 = success, 1 = invalid course
         *     RegisterRequest (List containing student info)
         */
        try
        {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
            var stub = HelloGrpc.newBlockingStub(channel);

            // Request the list and sort it by ascending order based on the studentID.
            var listResponse = stub.list(Messages.ListRequest.newBuilder().setCourse(courseName).build());
            List<RegisterRequest> studentList = listResponse.getRegisterationsList();
            studentList.sort(Comparator.comparingInt(RegisterRequest::getSsid));

            for (RegisterRequest student : studentList)
            {
                System.out.println(student.getAddCode() + " " + student.getSsid() + " " + student.getName());
            }
        }
        catch (StatusRuntimeException e)
        {
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