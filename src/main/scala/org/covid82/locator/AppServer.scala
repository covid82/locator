package org.covid82.locator

import cats.effect.{ConcurrentEffect, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import AppRoutes._
import org.http4s.implicits._

object AppServer {
  def stream[F[_] : ConcurrentEffect : Timer]: Stream[F, Nothing] =
    BlazeServerBuilder[F]
      .bindHttp(host = "0.0.0.0", port = 8080)
      .withHttpApp(monitoringRoutes[F].orNotFound)
      .serve
      .drain
}
