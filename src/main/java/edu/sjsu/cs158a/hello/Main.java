package edu.sjsu.cs158a.hello;

import edu.sjsu.cs158a.hello.Messages.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Command
public class Main
{
    /**
     * Registers a student in CS158A/CS158B by performing a remote procedure call (gRPC).
     * Requires the server to be run (using the server argument).
     *
     * @param hostPort (String) The IP:Port to connect to.
     * @param courseName (String) The course to enroll in. Only CS158A/CS158B are acceptable per assignment details.
     * @param SSID (int) The unique ID that identifies a specific student.
     * @param name (String) The name of a student.
     */
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
            // Requesting AddCode Section (Remote Procedure Call to requestCode())
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

            // Registration Section (Remote Procedure Call to register())
            var registrationRequest = Messages.RegisterRequest.newBuilder()
                    .setAddCode(addCode)
                    .setSsid(SSID)
                    .setName(name).build();
            var registrationResponse = stub.register(registrationRequest);

            // RC: 0 = Success, 1 = Invalid add code, 2 = Code does not match SSID.
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

    /**
     * Lists the current students in ascending order based on SSID, given a class name.
     *
     * @param hostPort (String) Describes the IP:Port to connect to.
     * @param courseName (String) A course name to lookup students for. Only CS158A/CS158B are supported.
     */
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

            // sort() fails on List implementation, wrap into ArrayList to use. Sort in ascending order by SSID.
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


    /**
     * Runs a server that listens on a port (which clients can connect to).
     * Implements 3 remote procedure calls: requestCode(), register(), and list().
     *
     * @param port (int) A port number that the server is listening on.
     */
    @Command
    void server(@Parameters(paramLabel = "port") int port) throws InterruptedException
    {
        class HelloImpl extends HelloGrpc.HelloImplBase
        {
            // Ties a student (in the form of a RegisterRequest) with a course (String).
            // List of students that have requested an addCode but haven't been registered.
            final ConcurrentHashMap<RegisterRequest, String> draftStudentList = new ConcurrentHashMap<>();

            // Atomic [For synchronization] because no student can have the same addCode.
            final AtomicInteger addCodeCounter = new AtomicInteger(1);

            // List of successfully registered students (in the form of a RegisterRequest).
            // <SSID, Student> format. Cannot have multiple RegisterRequest values with the same SSID key.
            // A student cannot concurrently enroll in both CS158A & CS158B.
            final ConcurrentHashMap<Integer, RegisterRequest> registeredStudentList = new ConcurrentHashMap<>();

            /**
             * Remote Procedure Call from a client which supplies a CodeRequest.
             * Returns a CodeResponse (Through the StreamObserver) if the CodeRequest
             * contains a valid course name and valid SSID.
             *
             * @param request (CodeRequest) Contains a course name and student ID.
             * @param responseObserver (StreamObserver) Used to return a CodeResponse to the client.
             */
            @Override
            public void requestCode(CodeRequest request, StreamObserver<CodeResponse> responseObserver)
            {
                String course = request.getCourse();
                int SSID = request.getSsid();

                // Check if the course is not valid. Only CS158A/CS158B are valid course names (assignment description).
                if (!course.equalsIgnoreCase("CS158A") && !course.equalsIgnoreCase("CS158B"))
                {
                    // Return an error (code specified in proto file).
                    var response = Messages.CodeResponse.newBuilder().setRc(1).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // Validate ID: Only valid if (100,000 <= ID < 90,000,000)
                if (SSID < 100_000 || SSID >= 90_000_000)
                {
                    // Return an error if the SSID is within an invalid range.
                    var response = Messages.CodeResponse.newBuilder().setRc(2).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // From this point on, the course and student ID are valid.

                // Used to avoid a ConcurrentModificationException.
                RegisterRequest temporaryStudent = null;

                // Check to see if this is an overwrite of a record. If so, delete it from draftStudentList.
                // If a student registered in CS158A but wanted to change their name, the first request needs to
                // be removed or the register() method will lock on the first request since it finds requests by SSID.
                for (RegisterRequest student : draftStudentList.keySet())
                {
                    // Indicates an overwrite.
                    if (request.getSsid() == student.getSsid())
                    {
                        temporaryStudent = student;
                    }
                }

                // Remove the initial request if an overwrite is detected.
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

            /**
             * Remote Procedure Call from a client which supplies a RegisterRequest.
             * Returns a RegisterResponse (Through the StreamObserver) if the CodeRequest
             * contains an add code that has previously been created and the add code is
             * for the specific student/SSID.
             *
             * @param request (RegisterRequest) Contains an add code, SSID, and name of the student.
             * @param responseObserver (StreamObserver) Used to return a RegisterResponse to the client.
             */
            @Override
            public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver)
            {
                // It's important to check for SSID matching add code first, rather than the
                // validity of the add code. For some reason, doing add code validity first
                // causes issues with the Autograder.

                // Find the student using the SSID in draftStudentList.
                for (Map.Entry<RegisterRequest, String> entry : draftStudentList.entrySet())
                {
                    if (request.getSsid() == entry.getKey().getSsid())
                    {
                        // If the add code from the "request" parameter doesn't match up with what was stored,
                        // transmit an error as the add code does not belong to that student.
                        if (request.getAddCode() != entry.getKey().getAddCode())
                        {
                            var response = Messages.RegisterResponse.newBuilder().setRc(2).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                            return;
                        }
                    }

                    // Make sure the client did not send a random, made-up add code that never existed.
                    // Add codes are valid if they appear in draftStudentList, i.e., add codes were stored
                    // when they were created. If an add code does not appear in the stored list, it is invalid.
                    boolean addCodeFound = false;

                    for (RegisterRequest student : draftStudentList.keySet())
                    {
                        if (student.getAddCode() == request.getAddCode())
                        {
                            addCodeFound = true;
                            break;
                        }
                    }

                    // If the add code was not found in draftStudentList, it is invalid.
                    if (!addCodeFound)
                    {
                        var response = Messages.RegisterResponse.newBuilder().setRc(1).build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                        return;
                    }
                }

                // Otherwise, send a success message and register the student in the class.
                var response = Messages.RegisterResponse.newBuilder().setRc(0).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

                registeredStudentList.put(request.getSsid(), request);
            }

            /**
             * Remote Procedure Call from a client which supplies a ListRequest.
             * Returns a ListResponse (Through the StreamObserver) which contains a return code for
             * status conditions and a List which contains RegisterRequests (each one represents a student).
             *
             * @param request (ListRequest) Identifies a course to list the students for.
             * @param responseObserver (StreamObserver) Used to return a ListResponse to the client.
             */
            @Override
            public void list(ListRequest request, StreamObserver<ListResponse> responseObserver)
            {
                String course = request.getCourse();

                // Check to see if the course name is not either CS158A/CS158B.
                if (!course.equalsIgnoreCase("CS158A") && !course.equalsIgnoreCase("CS158B"))
                {
                    // Return an error because the course name is not a valid course.
                    var response = Messages.ListResponse.newBuilder().setRc(1).build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }

                // Return a List of students (In the form of RegisterRequests).
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