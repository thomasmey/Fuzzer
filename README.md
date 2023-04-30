# Setup
Run target with

-javaagent:[yourpath/]jacocoagent.jar=[option1]=[value1],[option2]=[value2]
output=tcpserver

-javaagent:%USERPROFILE%\.m2\repository\org\jacoco\org.jacoco.agent\0.8.10\org.jacoco.agent-0.8.10-runtime.jar=output=tcpserver

https://www.eclemma.org/jacoco/
https://www.jacoco.org/jacoco/trunk/doc/agent.html

mvn dependency:get -Dartifact=org.jacoco:org.jacoco.agent:0.8.10:jar:runtime
