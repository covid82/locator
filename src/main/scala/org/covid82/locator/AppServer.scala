package org.covid82.locator

import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._

object AppServer {
  def stream[F[_] : ConcurrentEffect : ContextShift : Timer](config: FtpConfig): Stream[F, Nothing] = {
    for {
      registry <- RipeService[F](config).read
      _ <- Stream.eval_(Sync[F].delay(registry.take(10).foreach(println)))
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(AppRoutes.monitoringRoutes[F].orNotFound)
        .serve
    } yield exitCode
  }.drain
}