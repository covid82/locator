package org.covid82.locator

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._
import cats.syntax.semigroupk._
import AppRoutes._
import natchez.EntryPoint
import fs2.concurrent.SignallingRef
import cats.effect.syntax.concurrent._
import fs2.aws.sqs.{SQSConsumerBuilder, SqsConfig}
import fs2.aws.sqsStream
import javax.jms.{Message, TextMessage}
import cats.syntax.apply._
import eu.timepit.refined.types.string.TrimmedString

object AppServer {

  case class Ping(text: String)

  implicit val messageDecoder: Message => Either[Throwable, Ping] = { sqs_msg =>
    import io.circe.generic.auto._
    io.circe.parser.decode[Ping](sqs_msg.asInstanceOf[TextMessage].getText)
  }

  val sqsConfig: SqsConfig = SqsConfig(TrimmedString.trim("https://sqs.eu-central-1.amazonaws.com/489683348645/registry-notification"))

  def stream[F[_] : ConcurrentEffect : ContextShift : Timer](
    registryReader: RegistryReader[F],
    registryRef: SignallingRef[F, Option[IpRegistry]]
  )(implicit
    blocker: Blocker,
    entryPoint: EntryPoint[F]
  ): Stream[F, Nothing] = {
    for {
      ripeService <- Stream(RipeService[F](registryReader, registryRef))
      _ <- sqsStream[F, Ping](sqsConfig, SQSConsumerBuilder(_, _))
        .evalMap(m => ripeService.read *> Sync[F].delay(println(m)))
      _ <- Stream.eval(ripeService.read.start)
      routes = monitoringRoutes[F](ripeService) <+>
        staticFilesRoute(blocker) <+>
        apiRoutes[F](ripeService)
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .serve
    } yield exitCode
  }.drain
}