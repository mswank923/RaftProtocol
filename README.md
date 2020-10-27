# RaftProtocol
This project implements the raft protocol for distributed systems

A tutorial for the raft protocol can be found at the following link:
http://thesecretlivesofdata.com/raft/

To run this project using docker consider the following:

For Docker containers:
# Docker Container
Build image with
```
docker build -t csci251:latest -f Dockerfile-raftprotocol
```

Verify the new image with
```
docker images
```

Start a container
```
docker run -it csci251 /bin/bash
```

For docker-compose:
#Docker-compose
Build and start docker containers
```
docker-compose -f docker-compose-raftprotocol.yml up
```
use `--build` to rebuild docker image

To attach to separate running docker containers use the following command:
```
docker exec -it <CONTAINER-NAME> bash
```

where `<CONTAINER-NAME>` should be replaced with you targeted container name.

#Running the Program
When you build and start the docker containers with 
the docker-compose file, then 5 different containers (nodes)
will automatically run the program.