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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
            var codeRequest = Messages.CodeRequest.newBuilder().setCourse(courseName)
                    .setSsid(SSID).build();
            var codeResponse = stub.requestCode(codeRequest);

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
            var registrationRequest = Messages.RegisterRequest.newBuilder()
                    .setAddCode(addCode)
                    .setSsid(SSID)
                    .setName(name).build();
            var registrationResponse = stub.register(registrationRequest);

            int registrationResponseStatus = registrationResponse.getRc();

            if (registrationResponseStatus == 0)
            {
                System.out.println("registration successful");
            }
            else
            {
                System.out.println("problem registering: " + registrationResponseStatus);
            }
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

            // If the course that was requested wasn't CS158A/CS158B, display the error (listResponse rc = 1).
            if (listResponse.getRc() == 1)
            {
                System.out.println("problem listing students: " + listResponse.getRc());
                return;
            }

            ArrayList<RegisterRequest> studentList = new ArrayList<>(listResponse.getRegisterationsList());
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
    void server(@Parameters(paramLabel = "port") int port) throws InterruptedException
    {
        class HelloImpl extends HelloGrpc.HelloImplBase
        {
            // Ties a student (in the form of a RegisterRequest) with a course (String).
            // List of students that have requested an addCode but haven't been registered.
            ConcurrentHashMap<RegisterRequest, String> draftStudentList = new ConcurrentHashMap<>();
            // Atomic because no student can have the same addCode.
            AtomicInteger addCodeCounter = new AtomicInteger(1);

            // List of successfully registered students (in the form of a RegisterRequest).
            // <SSID, Student> format. Cannot have multiple entries with the same SSID key.
            // A student cannot concurrently enroll in both CS158A & CS158B.
            ConcurrentHashMap<Integer, RegisterRequest> registeredStudentList = new ConcurrentHashMap<>();

            @Override
            public void requestCode(CodeRequest request, StreamObserver<CodeResponse> responseObserver)
            {
                String course = request.getCourse();
                int SSID = request.getSsid();

                // Check if the course is not valid.
                if (!course.equalsIgnoreCase("CS158A") && !course.equalsIgnoreCase("CS158B"))
                {
                    var response = Messages.CodeResponse.newBuilder().setRc(1).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // Validate ID: Only valid if (100,000 <= ID < 90,000,000)
                if (SSID < 100_000 || SSID >= 90_000_000)
                {
                    var response = Messages.CodeResponse.newBuilder().setRc(2).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // From this point on, the course and student ID are valid.

                // Used to avoid a ConcurrentModificationException.
                RegisterRequest temporaryStudent = null;

                // Check to see if this is an overwrite of a record. If so, delete it from draftStudentList.
                for (RegisterRequest student : draftStudentList.keySet())
                {
                    // Indicates an overwrite.
                    if (request.getSsid() == student.getSsid())
                    {
                        temporaryStudent = student;
                    }
                }

                if (temporaryStudent != null)
                    draftStudentList.remove(temporaryStudent);

                int addCode = addCodeCounter.getAndIncrement();

                // Add the future student to draftStudentList.
                draftStudentList.put(Messages.RegisterRequest.newBuilder().setAddCode(addCode)
                        .setSsid(SSID).setName("").build(), course);

                // Send a CodeResponse to the client.
                var response = Messages.CodeResponse.newBuilder().setRc(0)
                        .setAddcode(addCode).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver)
            {
                // Make sure the client did not send a random, made-up add code that never existed.
                boolean addCodeFound = false;

                for (RegisterRequest student : draftStudentList.keySet())
                {
                    if (student.getAddCode() == request.getAddCode())
                    {
                        addCodeFound = true;
                        break;
                    }
                }

                if (!addCodeFound)
                {
                    var response = Messages.RegisterResponse.newBuilder().setRc(1).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // Find the student using the SSID in draftStudentList.
                for (Map.Entry<RegisterRequest, String> entry : draftStudentList.entrySet())
                {
                    // Assume the SSID from "request" parameter matches at least one SSID in draftStudentList.
                    if (request.getSsid() == entry.getKey().getSsid())
                    {
                        // If the add code from the "request" parameter doesn't match up with what was stored, error.
                        if (request.getAddCode() != entry.getKey().getAddCode())
                        {
                            var response = Messages.RegisterResponse.newBuilder().setRc(2).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                            return;
                        }
                    }
                }

                var response = Messages.RegisterResponse.newBuilder().setRc(0).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

                registeredStudentList.put(request.getSsid(), request);
            }

            @Override
            public void list(ListRequest request, StreamObserver<ListResponse> responseObserver)
            {
                String course = request.getCourse();

                if (!course.equalsIgnoreCase("CS158A") && !course.equalsIgnoreCase("CS158B"))
                {
                    var response = Messages.ListResponse.newBuilder().setRc(1).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                var response = Messages.ListResponse.newBuilder().setRc(0)
                        .addAllRegisterations(registeredStudentList.values()).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }

        try
        {
            var server = ServerBuilder.forPort(port).addService(new HelloImpl()).build();
            server.start();
            server.awaitTermination();
        }
        catch (IOException e)
        {
            System.out.println("couldn't serve on " + port);
        }
    }
    public static void main(String[] args)
    {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}