package org.covid82.locator

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._
import cats.syntax.semigroupk._
import AppRoutes._
import cats.Applicative

object AppServer {
  def stream[F[_] : ConcurrentEffect : ContextShift : Timer](config: FtpConfig, blocker: Blocker): Stream[F, Nothing] = {
    for {
      ripeService <- Stream.eval(Applicative[F].pure(RipeService[F](config)))
      registry <- ripeService.read
      routes = monitoringRoutes[F] <+> staticFilesRoute(blocker) <+> apiRoutes[F](ripeService, registry)
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .serve
    } yield exitCode
  }.drain
}