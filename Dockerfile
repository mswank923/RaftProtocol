# Ubuntu 18.04 with JDK 11
# Build image with:  docker build -t csci251:latest .

FROM ubuntu:18.04
MAINTAINER Shakeel Farooq, Max Swank

# install all dependencies
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y  software-properties-common && \
    apt-get update && \
    apt-get install -y openjdk-11-jdk && \
    apt-get install -y net-tools iputils-ping maven gradle nmap wget git vim build-essential && \
    apt-get clean

# create a new directory as the working directory
RUN mkdir /csci251


# copy files from the directory of the Dockerfile on your computer to this docker build environment.
COPY basic_word_count /csci251/basic_word_count
COPY distributed_hash_table /csci251/distributed_hash_table
COPY distributed_consensus /csci251/distributed_consensus
COPY gossip_protocol /csci251/gossip_protocol
COPY pubsub /csci251/pubsub
COPY remote_procedure_call /csci251/remote_procedure_call
COPY remote_method_invocation /csci251/remote_method_invocation
COPY socket_programming /csci251/socket_programming
COPY pom.xml /csci251/
COPY README.md /csci251/
COPY LICENSE /csci251/

# You will need add COPY commands to copy your project source code into the docker build environment.
# e.g., COPY project1 /csci251/project1

# setup working directory in the container
WORKDIR /csci251

# go into the working directory and build java package using maven
RUN cd /csci251 && mvn package
