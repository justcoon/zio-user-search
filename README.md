# zio user search

search/serving service for users 

service is consuming user domain events from kafka topic and indexing them to elasticsearch

service got gRpc, REST and GraphQL api for user search

see also [akka-typed-user](https://github.com/justcoon/akka-typed-user)

# required

* elasticsearch
* kafka