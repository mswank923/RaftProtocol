# RaftProtocol
This project implements and demonstrates the Raft Protocol for distributed systems.

Follow [this](http://thesecretlivesofdata.com/raft/) link for a visual demonstration of the Raft 
Protocol.

## Docker
Build image with:
```
docker build -t raft-node:latest -f Dockerfile-raftprotocol
```

See the new image with:
```
docker images
```

Start a container:
```
docker run -it raft-node /bin/bash
```

##Docker-compose
Start 5 docker containers each running Node.RaftNode:
```
docker-compose -f docker-compose-raftprotocol.yml up
```
use `--build` to rebuild docker image

To attach the shell to a running container:
```
docker exec -it <CONTAINER-NAME> bash
```

where `<CONTAINER-NAME>` should be replaced with the name of the targeted container.
