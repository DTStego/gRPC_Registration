# gRPC_Registration
gRPC implementation of a basic class registration system with some remote procedure calls.

# Installation
Clone the project in the IDE of your choice and compile using Maven (Pom.xml).

Set the two compiled folders under target -> generated-sources -> protobuf to "Source Directories".

Run Main.java using the server command ("Server" "Port Number").
  Example: server 3333

There are two client commands that are currently implemented:
  
Register:
  "register" "host and port" "class name" "student ID" "student name"
  Note: Class name only supports "CS158A" or "CS158B".
  Example: register 127.0.0.1:3333 CS158A 19151 Ben
  
ListStudents:
  "listStudents" "host and port" "class name"
  Example: listStudents" "127.0.0.1:3333" "CS158A"
  
Uses Picocli for command line arguments.
  
