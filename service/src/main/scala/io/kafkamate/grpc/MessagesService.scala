package io.kafkamate
package grpc

import io.grpc.Status
import zio.{URLayer, ZEnv, ZIO, ZLayer}
import zio.stream.ZStream
import zio.logging._

import kafka.KafkaConsumer
import kafka.KafkaProducer
import messages._
import utils._

object MessagesService {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer with Logging

  lazy val liveLayer: URLayer[ZEnv with Logging, Env] =
    ZEnv.any ++ KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer ++ ZLayer.requires[Logging]

  object GrpcService extends ZioMessages.RMessagesService[Env] {
    override def produceMessage(request: ProduceRequest): ZIO[Env, Status, ProduceResponse] =
      KafkaProducer
        .produce(request.topicName, request.key, request.value)(request.clusterId)
        .tapError(e => log.error(s"Producer error: ${e.getMessage}"))
        .bimap(GRPCStatus.fromThrowable, _ => ProduceResponse("OK"))

    override def consumeMessages(request: ConsumeRequest): ZStream[Env, Status, Message] =
      KafkaConsumer
        .consumeStream(request.topicName, request.maxResults)(request.clusterId)
        .onError(e => log.error("Consumer error: \n" + e.prettyPrint))
        .mapError(GRPCStatus.fromThrowable)
  }
}
